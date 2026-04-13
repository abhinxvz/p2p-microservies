import { useState, useRef, useEffect, useCallback } from 'react';
import { networkService } from '@/services/api';

const CHUNK_SIZE = 64 * 1024; // 64 KB chunks for WebRTC DataChannel optimal transfer
const BUFFERED_AMOUNT_LOW_THRESHOLD = 1 * 1024 * 1024;  // 1 MB — resume sending when buffer drains to this
const MAX_BUFFERED_AMOUNT = 16 * 1024 * 1024;            // 16 MB — pause sending when buffer exceeds this

type FileTransferMetadata = {
  fileName: string;
  fileSize: number;
  fileType: string;
};

export function useWebRTC(currentUserId: string) {
  // Dictionary: peerId -> RTCPeerConnection
  const connections = useRef<Map<string, RTCPeerConnection>>(new Map());
  // Dictionary: peerId -> RTCDataChannel
  const channels = useRef<Map<string, RTCDataChannel>>(new Map());
  const ws = useRef<WebSocket | null>(null);

  const [connectedPeers, setConnectedPeers] = useState<string[]>([]);
  const [transferProgress, setTransferProgress] = useState(0);

  // Initialize unified signaling WebSocket
  useEffect(() => {
    if (!currentUserId) return;

    // Connect to file-transfer-service WebSocket signaling server via API Gateway
    ws.current = new WebSocket(`ws://localhost:8080/file-transfer/ws?userId=${currentUserId}`);

    ws.current.onmessage = async (event) => {
      const message = JSON.parse(event.data);
      const { type, senderId, targetId, payload } = message;

      if (targetId !== currentUserId) return;

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
        if (pc) await pc.addIceCandidate(new RTCIceCandidate(payload));
      }
    };

    return () => {
      ws.current?.close();
      connections.current.forEach(pc => pc.close());
    };
  }, [currentUserId]);

  const sendSignal = (targetId: string, type: string, payload: any) => {
    if (ws.current?.readyState === WebSocket.OPEN) {
      ws.current.send(JSON.stringify({ type, senderId: currentUserId, targetId, payload }));
    }
  };

  const createPeerConnection = (peerId: string): RTCPeerConnection => {
    if (connections.current.has(peerId)) {
      return connections.current.get(peerId)!;
    }

    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
    });

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignal(peerId, 'ice-candidate', event.candidate);
      }
    };

    pc.ondatachannel = (event) => {
      const channel = event.channel;
      setupDataChannel(peerId, channel);
    };

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed') {
        connections.current.delete(peerId);
        channels.current.delete(peerId);
        setConnectedPeers(prev => prev.filter(id => id !== peerId));
      }
    };

    connections.current.set(peerId, pc);
    return pc;
  };

  const setupDataChannel = (peerId: string, channel: RTCDataChannel) => {
    channel.binaryType = 'arraybuffer';
    channel.bufferedAmountLowThreshold = BUFFERED_AMOUNT_LOW_THRESHOLD;
    
    channel.onopen = () => {
      channels.current.set(peerId, channel);
      setConnectedPeers(prev => [...new Set([...prev, peerId])]);
    };

    channel.onclose = () => {
      channels.current.delete(peerId);
      setConnectedPeers(prev => prev.filter(id => id !== peerId));
    };

    channel.onmessage = (event) => {
      // In a real implementation you would handle receiving byte chunks and stitching them into a Blob
      console.log(`Received data from ${peerId}:`, event.data);
    };
  };

  const connectToPeer = async (targetId: string) => {
    const pc = createPeerConnection(targetId);
    
    // Create reliable data channel for file transfer
    const channel = pc.createDataChannel('fileTransfer', { ordered: true });
    setupDataChannel(targetId, channel);

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    sendSignal(targetId, 'offer', pc.localDescription);
  };

  /**
   * Waits until the channel's bufferedAmount drops below MAX_BUFFERED_AMOUNT.
   * Uses the `onbufferedamountlow` event so we don't busy-wait.
   */
  const waitForBufferDrain = (channel: RTCDataChannel): Promise<void> => {
    if (channel.bufferedAmount < MAX_BUFFERED_AMOUNT) {
      return Promise.resolve();
    }
    return new Promise<void>((resolve) => {
      const onLow = () => {
        channel.removeEventListener('bufferedamountlow', onLow);
        resolve();
      };
      channel.addEventListener('bufferedamountlow', onLow);
    });
  };

  /**
   * Broadcasts a chunk to all target peers with backpressure.
   * For each channel that has a full buffer, waits for it to drain
   * before sending to avoid the browser silently killing the connection.
   */
  const broadcastChunkWithBackpressure = async (chunk: ArrayBuffer, targetPeers: string[]) => {
    for (const peerId of targetPeers) {
      const channel = channels.current.get(peerId);
      if (channel && channel.readyState === 'open') {
        await waitForBufferDrain(channel);
        channel.send(chunk);
      }
    }
  };

  // WebRTC Mesh logic implementation
  const sendToGroup = async (groupId: number, file: File) => {
    try {
      // 1. Fetch active members in this group from the network service
      const response = await networkService.fetchGroupPeers(groupId);
      const activeMembers = response.data || [];
      // ActiveContactDTO has { username, sessionIds[] } — target all sessionIds
      const targetPeerIds: string[] = [];
      for (const dto of activeMembers) {
        for (const sid of (dto.sessionIds || [])) {
          if (sid !== currentUserId) {
            targetPeerIds.push(sid);
          }
        }
      }

      // 2. Initiate simultaneous WebRTC mesh negotiation for all active members
      await Promise.all(targetPeerIds.map((id: string) => connectToPeer(id)));

      // 3. Wait gracefully for all data channels to verify 'open' state (simplified for demo)
      await new Promise(resolve => setTimeout(resolve, 2000)); 

      // 4. File Slicing and Mesh Broadcasting loop
      const metadata: FileTransferMetadata = {
        fileName: file.name,
        fileSize: file.size,
        fileType: file.type
      };

      // Broadcast metadata as JSON first
      const metadataStr = JSON.stringify({ type: 'metadata', ...metadata });
      targetPeerIds.forEach(peerId => {
        const channel = channels.current.get(peerId);
        if (channel?.readyState === 'open') channel.send(metadataStr);
      });

      // Slice and Broadcast with backpressure
      let offset = 0;
      setTransferProgress(0);

      while (offset < file.size) {
        const slice = file.slice(offset, offset + CHUNK_SIZE);
        const arrayBuffer = await slice.arrayBuffer();
        
        await broadcastChunkWithBackpressure(arrayBuffer, targetPeerIds);
        
        offset += CHUNK_SIZE;
        setTransferProgress(Math.min(100, Math.round((offset / file.size) * 100)));
        
        // Yield to event loop to prevent locking the browser UI thread
        await new Promise(resolve => setTimeout(resolve, 0));
      }

      console.log(`Successfully broadcasted ${file.name} to mesh group ${groupId}`);
    } catch (err) {
      console.error('Group transmission failed', err);
    }
  };

  return {
    sendToGroup,
    connectToPeer,
    connectedPeers,
    transferProgress
  };
}
