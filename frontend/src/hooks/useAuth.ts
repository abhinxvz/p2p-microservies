import { useState, useEffect, useCallback } from 'react';
import { authService, peerService } from '../services/api';
import { v4 as uuidv4 } from 'uuid';

function extractUsernameFromToken(token: string): string | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.sub || null;
  } catch {
    return null;
  }
}

/**
 * Returns a stable, per-tab sessionId.
 * Generated once per browser tab and stored in sessionStorage so it
 * survives page refreshes but not new tabs.
 */
function getOrCreateSessionId(): string {
  let sessionId = sessionStorage.getItem('peer_session_id');
  if (!sessionId) {
    sessionId = uuidv4();
    sessionStorage.setItem('peer_session_id', sessionId);
  }
  return sessionId;
}

export function useAuth() {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);

  // --- Auth status listener ---
  useEffect(() => {
    const checkAuthStatus = () => {
      const token = localStorage.getItem('auth_token');
      setIsAuthenticated(!!token);
    };

    checkAuthStatus();
    window.addEventListener('authStatusChange', checkAuthStatus);
    return () => window.removeEventListener('authStatusChange', checkAuthStatus);
  }, []);

  // --- Beacon deregister on tab/browser close ---
  useEffect(() => {
    const handleBeforeUnload = () => {
      const token = localStorage.getItem('auth_token');
      if (!token) return;

      const sessionId = sessionStorage.getItem('peer_session_id');
      if (!sessionId) return;

      // navigator.sendBeacon only supports POST, so we use fetch with keepalive
      // for the DELETE method. keepalive ensures the request outlives the page.
      fetch(`http://localhost:8080/peers/${sessionId}/deregister`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        keepalive: true,
      }).catch(() => {});
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, []);

  const login = async (username: string, password: string) => {
    try {
      const data = await authService.authLogin(username, password);
      window.dispatchEvent(new Event('authStatusChange'));
      // Auto-register as an active peer immediately after login
      const sessionId = getOrCreateSessionId();
      try {
        await peerService.registerPeer(username, sessionId);
      } catch (e) {
        console.warn('Peer registration failed (may already be registered)', e);
      }
      return { success: true, data };
    } catch (error: any) {
      console.error('Login error', error);
      return { success: false, error: typeof error.response?.data === 'string' ? error.response.data : (error.response?.data?.error || error.response?.data?.message || 'Login failed') };
    }
  };

  const register = async (username: string, password: string) => {
    try {
      await authService.authRegister(username, password);
      return { success: true };
    } catch (error: any) {
      console.error('Register error', error);
      return { success: false, error: typeof error.response?.data === 'string' ? error.response.data : (error.response?.data?.error || error.response?.data?.message || 'Registration failed') };
    }
  };

  const logout = async () => {
    // Deregister peer BEFORE removing the token — fixes the race condition
    const token = localStorage.getItem('auth_token');
    if (token) {
      const sessionId = sessionStorage.getItem('peer_session_id');
      if (sessionId) {
        try {
          await peerService.deregisterPeer(sessionId);
        } catch {
          // Best-effort; don't block logout if the backend is down
        }
      }
    }
    // Only now remove the token — the Authorization header was available for the call above
    localStorage.removeItem('auth_token');
    sessionStorage.removeItem('peer_session_id');
    window.dispatchEvent(new Event('authStatusChange'));
  };

  return { isAuthenticated, login, register, logout };
}
