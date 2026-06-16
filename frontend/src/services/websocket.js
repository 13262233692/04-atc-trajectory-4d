import { useState, useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';

export function useWebSocket() {
  const clientRef = useRef(null);
  const [isConnected, setIsConnected] = useState(false);
  const subscriptionsRef = useRef(new Map());

  const connect = useCallback(() => {
    if (clientRef.current && clientRef.current.active) {
      console.log('WebSocket already connected');
      return;
    }

    try {
      const socket = new SockJS(WS_URL);
      const client = new Client({
        webSocketFactory: () => socket,
        reconnectDelay: 5000,
        heartbeatIncoming: 30000,
        heartbeatOutgoing: 30000,
        debug: (str) => {
          if (str.includes('error') || str.includes('ERROR')) {
            console.error('STOMP Debug:', str);
          }
        },
        onConnect: () => {
          console.log('WebSocket connected successfully');
          setIsConnected(true);
        },
        onDisconnect: () => {
          console.log('WebSocket disconnected');
          setIsConnected(false);
        },
        onStompError: (frame) => {
          console.error('STOMP Error:', frame);
          setIsConnected(false);
        },
        onWebSocketError: (error) => {
          console.error('WebSocket Error:', error);
          setIsConnected(false);
        },
        onWebSocketClose: () => {
          console.log('WebSocket closed');
          setIsConnected(false);
        },
      });

      client.activate();
      clientRef.current = client;
    } catch (error) {
      console.error('Failed to create WebSocket connection:', error);
      setIsConnected(false);
    }
  }, []);

  const disconnect = useCallback(() => {
    if (clientRef.current) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }
    subscriptionsRef.current.clear();
    setIsConnected(false);
  }, []);

  const subscribe = useCallback((destination, callback) => {
    if (!clientRef.current || !clientRef.current.active) {
      console.warn('Cannot subscribe: WebSocket not connected');
      return {
        unsubscribe: () => {},
      };
    }

    const existingSub = subscriptionsRef.current.get(destination);
    if (existingSub) {
      console.log('Already subscribed to:', destination);
      return existingSub;
    }

    try {
      const subscription = clientRef.current.subscribe(destination, (message) => {
        try {
          callback(message);
        } catch (error) {
          console.error('Error in message callback:', error);
        }
      });

      subscriptionsRef.current.set(destination, subscription);
      console.log('Subscribed to:', destination);

      return subscription;
    } catch (error) {
      console.error('Failed to subscribe to', destination, ':', error);
      return {
        unsubscribe: () => {},
      };
    }
  }, []);

  const unsubscribe = useCallback((destination) => {
    const subscription = subscriptionsRef.current.get(destination);
    if (subscription) {
      try {
        subscription.unsubscribe();
      } catch (error) {
        console.error('Error unsubscribing:', error);
      }
      subscriptionsRef.current.delete(destination);
      console.log('Unsubscribed from:', destination);
    }
  }, []);

  const send = useCallback((destination, body = {}, headers = {}) => {
    if (!clientRef.current || !clientRef.current.active) {
      console.warn('Cannot send message: WebSocket not connected');
      return;
    }

    try {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(body),
        headers,
      });
    } catch (error) {
      console.error('Failed to send message:', error);
    }
  }, []);

  useEffect(() => {
    return () => {
      disconnect();
    };
  }, [disconnect]);

  return {
    connect,
    disconnect,
    subscribe,
    unsubscribe,
    send,
    isConnected,
  };
}

export default useWebSocket;
