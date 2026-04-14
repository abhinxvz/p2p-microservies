import { useState, useRef, useEffect, useCallback } from 'react';
import { networkService } from '@/services/api';

const CHUNK_SIZE = 64 * 1024;
const BUFFERED_AMOUNT_LOW_THRESHOLD = 1 * 1024 * 1024;
const MAX_BUFFERED_AMOUNT = 16 * 1024 * 1024;

export function useWebRTC(currentUserId: string) {
  const connections = useRef<Map<string, RTCPeerConnection>>(new Map());
  const channels = useRef<Map<string, RTCDataChannel>>(new Map());
  const wsRef = useRef<WebSocket | null>(null);
  const currentUserIdRef = useRef(currentUserId);
  useEffect(() => { currentUserIdRef.current = currentUserId; }, [currentUserId]);

  const [connectedPeers, setConnectedPeers] = useState<string[]>([]);
  const [transferProgress, setTransferProgress] = useState(0);
  const [incomingRequest, setIncomingRequest] = useState<{ peerId: string; metadata: any } | null>(null);
  const transferApprovalWaits = useRef<Map<string, () => void>>(new Map());

  // ── 1. sendSignal — no deps, reads from refs only ─────────────────────────
  const sendSignal = useCallback((targetId: string, type: string, payload: any) => {
    const ws = wsRef.current;
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ targetId, type, sourcePeerId: currentUserIdRef.current, payload }));
    } else {
      console.warn('[WS] Socket not open, dropping signal type:', type);
    }
  }, []); // stable — only reads refs

  // ── 2. setupDataChannel — must be defined BEFORE createPeerConnection ─────
  const setupDataChannel = useCallback((peerId: string, channel: RTCDataChannel) => {
    channel.binaryType = 'arraybuffer';
    channel.bufferedAmountLowThreshold = BUFFERED_AMOUNT_LOW_THRESHOLD;

    // Receiver state for assembling incoming file
    let receivingMeta: { fileName: string; fileSize: number; fileType: string } | null = null;
    const receivedChunks: ArrayBuffer[] = [];
    let receivedBytes = 0;

    channel.onopen = () => {
      channels.current.set(peerId, channel);
      setConnectedPeers((prev) => [...new Set([...prev, peerId])]);
    };

    channel.onclose = () => {
      channels.current.delete(peerId);
      setConnectedPeers((prev) => prev.filter((id) => id !== peerId));
    };

    channel.onmessage = (event) => {
      // ── Binary chunk: accumulate and auto-save when complete ──────────────
      if (event.data instanceof ArrayBuffer) {
        if (!receivingMeta) return; // no metadata yet, ignore
        receivedChunks.push(event.data);
        receivedBytes += event.data.byteLength;

        if (receivedBytes >= receivingMeta.fileSize) {
          // All chunks received — assemble and trigger browser download
          const blob = new Blob(receivedChunks, { type: receivingMeta.fileType || 'application/octet-stream' });
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = receivingMeta.fileName;
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          URL.revokeObjectURL(url);
          // Reset for next transfer
          receivingMeta = null;
          receivedChunks.length = 0;
          receivedBytes = 0;
        }
        return;
      }

      // ── Text frame: JSON control message ─────────────────────────────────
      try {
        const msg = JSON.parse(event.data as string);
        if (msg.type === 'metadata') {
          // Reset state and store metadata for incoming file
          receivingMeta = { fileName: msg.fileName, fileSize: msg.fileSize, fileType: msg.fileType };
          receivedChunks.length = 0;
          receivedBytes = 0;
          setIncomingRequest({ peerId, metadata: msg });
        } else if (msg.type === 'transfer_accepted') {
          const resolve = transferApprovalWaits.current.get(peerId);
          if (resolve) { resolve(); transferApprovalWaits.current.delete(peerId); }
        }
      } catch {
        console.warn('[DataChannel] Non-JSON message from', peerId);
      }
    };
  }, []); // stable — only reads refs and sets state

  // ── 3. createPeerConnection — depends on sendSignal + setupDataChannel ────
  const createPeerConnection = useCallback((peerId: string): RTCPeerConnection => {
    if (connections.current.has(peerId)) return connections.current.get(peerId)!;

    const pc = new RTCPeerConnection({
      iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
      ],
    });

    pc.onicecandidate = (e) => {
      if (e.candidate) sendSignal(peerId, 'ice-candidate', e.candidate);
    };

    pc.ondatachannel = (e) => setupDataChannel(peerId, e.channel);

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed') {
        connections.current.delete(peerId);
        channels.current.delete(peerId);
        setConnectedPeers((prev) => prev.filter((id) => id !== peerId));
      }
    };

    connections.current.set(peerId, pc);
    return pc;
  }, [sendSignal, setupDataChannel]);

  // ── 4. handleSignal — depends on createPeerConnection + sendSignal ────────
  const handleSignal = useCallback(async (message: any) => {
    const { type, sourcePeerId: senderId, payload } = message;
    if (type === 'offer') {
      const pc = createPeerConnection(senderId);
      await pc.setRemoteDescription(new RTCSessionDescription(payload));
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      sendSignal(senderId, 'answer', pc.localDescription);
    } else if (type === 'answer') {
      const pc = connections.current.get(senderId);
      if (pc) await pc.setRemoteDescription(new RTCSessionDescription(payload));
    } else if (type === 'ice-candidate') {
      const pc = connections.current.get(senderId);
      if (pc && payload) await pc.addIceCandidate(new RTCIceCandidate(payload));
    }
  }, [createPeerConnection, sendSignal]);

  // ── 5. WebSocket — reconnects on currentUserId change ────────────────────
  useEffect(() => {
    if (!currentUserId) return;

    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let active = true;

    const connect = () => {
      if (!active) return;
      const ws = new WebSocket(`ws://localhost:8083/ws?userId=${encodeURIComponent(currentUserId)}`);
      ws.onopen = () => console.log('[WS] Connected as', currentUserId);
      ws.onmessage = (event) => {
        try { handleSignal(JSON.parse(event.data)); }
        catch (e) { console.error('[WS] Parse error', e); }
      };
      ws.onerror = () => console.error('[WS] Error — is file-transfer-service running on :8083?');
      ws.onclose = (e) => {
        console.log('[WS] Closed code=', e.code, '— reconnecting in 3s');
        if (active) reconnectTimer = setTimeout(connect, 3000);
      };
      wsRef.current = ws;
    };

    connect();

    return () => {
      active = false;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      wsRef.current?.close();
      connections.current.forEach((pc) => pc.close());
      connections.current.clear();
      channels.current.clear();
    };
  }, [currentUserId, handleSignal]);

  // ── 6. connectToPeer ──────────────────────────────────────────────────────
  const connectToPeer = useCallback(async (targetId: string): Promise<void> => {
    if (channels.current.get(targetId)?.readyState === 'open') return;

    if (connections.current.has(targetId)) {
      connections.current.get(targetId)!.close();
      connections.current.delete(targetId);
      channels.current.delete(targetId);
    }

    // Wait for signaling socket up to 8s
    if (wsRef.current?.readyState !== WebSocket.OPEN) {
      await new Promise<void>((resolve, reject) => {
        const t = setTimeout(() => { clearInterval(iv); reject(new Error('Signaling socket not ready')); }, 8000);
        const iv = setInterval(() => {
          if (wsRef.current?.readyState === WebSocket.OPEN) {
            clearInterval(iv); clearTimeout(t); resolve();
          }
        }, 100);
      });
    }

    const pc = createPeerConnection(targetId);
    const channel = pc.createDataChannel('fileTransfer', { ordered: true });
    setupDataChannel(targetId, channel);

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    sendSignal(targetId, 'offer', pc.localDescription);

    await new Promise<void>((resolve, reject) => {
      const t = setTimeout(() =>
        reject(new Error(`Timeout: peer "${targetId}" did not respond — make sure they are logged in`)),
        20000
      );
      channel.addEventListener('open', () => { clearTimeout(t); resolve(); }, { once: true });
      const onStateChange = () => {
        if (pc.connectionState === 'failed') {
          clearTimeout(t);
          pc.removeEventListener('connectionstatechange', onStateChange);
          reject(new Error('WebRTC connection failed'));
        }
      };
      pc.addEventListener('connectionstatechange', onStateChange);
    });
  }, [createPeerConnection, setupDataChannel, sendSignal]);

  // ── 7. sendFile ───────────────────────────────────────────────────────────
  const sendFile = useCallback(async (targetId: string, file: File): Promise<void> => {
    setTransferProgress(0);
    await connectToPeer(targetId);

    const channel = channels.current.get(targetId);
    if (!channel || channel.readyState !== 'open') throw new Error('Data channel not open');

    channel.send(JSON.stringify({ type: 'metadata', fileName: file.name, fileSize: file.size, fileType: file.type }));

    let offset = 0;
    while (offset < file.size) {
      const ab = await file.slice(offset, offset + CHUNK_SIZE).arrayBuffer();
      await waitForBufferDrain(channel);
      channel.send(ab);
      offset += CHUNK_SIZE;
      setTransferProgress(Math.min(100, Math.round((offset / file.size) * 100)));
      await new Promise((r) => setTimeout(r, 0));
    }
  }, [connectToPeer]);

  // ── 8. Backpressure ───────────────────────────────────────────────────────
  const waitForBufferDrain = (channel: RTCDataChannel): Promise<void> => {
    if (channel.bufferedAmount < MAX_BUFFERED_AMOUNT) return Promise.resolve();
    return new Promise<void>((resolve) => {
      const fn = () => { channel.removeEventListener('bufferedamountlow', fn); resolve(); };
      channel.addEventListener('bufferedamountlow', fn);
    });
  };

  // ── 9. acceptIncomingTransfer ─────────────────────────────────────────────
  const acceptIncomingTransfer = useCallback(() => {
    if (!incomingRequest) return;
    const ch = channels.current.get(incomingRequest.peerId);
    if (ch?.readyState === 'open') ch.send(JSON.stringify({ type: 'transfer_accepted' }));
    setIncomingRequest(null);
  }, [incomingRequest]);

  // ── 10. sendToGroup ───────────────────────────────────────────────────────
  const sendToGroup = useCallback(async (groupId: number, file: File): Promise<void> => {
    const response = await networkService.fetchGroupPeers(groupId);
    const activeMembers = response.data || [];

    const targetPeerIds: string[] = [];
    for (const dto of activeMembers) {
      for (const sid of dto.sessionIds || []) {
        if (sid !== currentUserIdRef.current) targetPeerIds.push(sid);
      }
    }
    if (targetPeerIds.length === 0) throw new Error('No active peers in group');

    await Promise.all(targetPeerIds.map(connectToPeer));

    const metaStr = JSON.stringify({ type: 'metadata', fileName: file.name, fileSize: file.size, fileType: file.type });
    targetPeerIds.forEach((id) => {
      const ch = channels.current.get(id);
      if (ch?.readyState === 'open') ch.send(metaStr);
    });

    // Wait for all peers to accept (30s timeout)
    await Promise.all(targetPeerIds.map((peerId) =>
      new Promise<void>((resolve, reject) => {
        const t = setTimeout(() => {
          transferApprovalWaits.current.delete(peerId);
          reject(new Error(`Peer ${peerId} did not accept within 30s`));
        }, 30000);
        transferApprovalWaits.current.set(peerId, () => { clearTimeout(t); resolve(); });
      })
    ));

    let offset = 0;
    setTransferProgress(0);
    while (offset < file.size) {
      const ab = await file.slice(offset, offset + CHUNK_SIZE).arrayBuffer();
      for (const peerId of targetPeerIds) {
        const ch = channels.current.get(peerId);
        if (ch?.readyState === 'open') { await waitForBufferDrain(ch); ch.send(ab); }
      }
      offset += CHUNK_SIZE;
      setTransferProgress(Math.min(100, Math.round((offset / file.size) * 100)));
      await new Promise((r) => setTimeout(r, 0));
    }
  }, [connectToPeer]);

  return { sendFile, sendToGroup, connectToPeer, connectedPeers, transferProgress, incomingRequest, acceptIncomingTransfer };
}
