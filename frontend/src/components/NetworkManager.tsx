'use client';

import { useState, useEffect } from 'react';
import { networkService, peerService } from '@/services/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Users, UserPlus, Plus } from 'lucide-react';

export default function NetworkManager() {
  const [contacts, setContacts] = useState<any[]>([]);
  const [groups, setGroups] = useState<any[]>([]);
  const [incomingRequests, setIncomingRequests] = useState<any[]>([]);
  
  const [searchUsername, setSearchUsername] = useState('');
  const [addMessage, setAddMessage] = useState<{type: 'error' | 'success', text: string} | null>(null);
  const [groupName, setGroupName] = useState('');
  const [selectedForGroup, setSelectedForGroup] = useState<string[]>([]);

  const fetchNetwork = async () => {
    // Don't attempt network calls without a valid token
    if (!localStorage.getItem('auth_token')) return;

    // Fetch contacts & incoming requests
    try {
      const [contactsRes, requestsRes] = await Promise.all([
        networkService.fetchAllContacts(),
        networkService.fetchIncomingRequests(),
      ]);
      setContacts(contactsRes.data || []);
      setIncomingRequests(requestsRes.data || []);
    } catch (err: any) {
      // Fall back to the active-only endpoint if /contacts is 404
      if (err.response?.status === 404) {
        try {
          const activeRes = await networkService.fetchContactPeers();
          const activeData = (activeRes.data || []).map((c: any) => ({
            ...c,
            online: true,
          }));
          setContacts(activeData);
        } catch { /* silently fail */ }
      } else {
        console.error('Failed to load contacts', err);
      }
    }

    // Fetch groups independently
    try {
      const res = await networkService.fetchMyGroups();
      setGroups(res.data || []);
    } catch (err) {
      console.error('Failed to load groups', err);
    }
  };

  useEffect(() => {
    fetchNetwork();
    const interval = setInterval(fetchNetwork, 10000);
    // Also refresh immediately when user logs in
    window.addEventListener('authStatusChange', fetchNetwork);
    return () => {
      clearInterval(interval);
      window.removeEventListener('authStatusChange', fetchNetwork);
    };
  }, []);

  const handleAddContact = async (username: string) => {
    if (!username.trim()) return;
    setAddMessage(null);
    try {
      const res = await networkService.addContact(username);
      setAddMessage({ type: 'success', text: res.data.message || 'Request sent!' });
      setSearchUsername('');
      fetchNetwork();
    } catch (err: any) {
      const errorText = err.response?.data?.error || 'Failed to send request';
      setAddMessage({ type: 'error', text: errorText });
    }
  };

  const handleAcceptRequest = async (id: number) => {
    try {
      await networkService.acceptRequest(id);
      fetchNetwork();
    } catch (err) {
      console.error('Failed to accept request', err);
    }
  };

  const handleRejectRequest = async (id: number) => {
    try {
      await networkService.rejectRequest(id);
      fetchNetwork(); // refresh lists
    } catch (err) {
      console.error('Failed to reject request', err);
    }
  };

  const handleCreateGroup = async () => {
    if (!groupName || selectedForGroup.length === 0) return;
    try {
      await networkService.createGroup(groupName, selectedForGroup);
      setGroupName('');
      setSelectedForGroup([]);
      fetchNetwork();
    } catch (err) {
      console.error('Create group failed', err);
    }
  };

  const toggleGroupSelection = (username: string) => {
    setSelectedForGroup(prev => 
      prev.includes(username) ? prev.filter(u => u !== username) : [...prev, username]
    );
  };

  // Sort contacts: online first, then alphabetical
  const sortedContacts = [...contacts].sort((a, b) => {
    if (a.online === b.online) return a.username.localeCompare(b.username);
    return a.online ? -1 : 1;
  });

  const onlineCount = contacts.filter(c => c.online).length;

  return (
    <div className="grid md:grid-cols-2 gap-6 mb-8">
      {/* Search & Contacts */}
      <Card className="border-none shadow-xl bg-white dark:bg-[#111113] rounded-3xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><UserPlus className="text-primary" /> Contacts</CardTitle>
          <CardDescription>
            {contacts.length} contact{contacts.length !== 1 ? 's' : ''} · {onlineCount} online
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-col gap-2">
            <div className="flex gap-2">
              <Input 
                placeholder="Search username to add..." 
                value={searchUsername} 
                onChange={e => setSearchUsername(e.target.value)} 
                className="bg-slate-50 dark:bg-zinc-900 rounded-xl"
              />
              <Button onClick={() => handleAddContact(searchUsername)} disabled={!searchUsername} className="rounded-xl bg-primary text-black font-bold">Add</Button>
            </div>
            {addMessage && (
              <p className={`text-xs ml-1 ${addMessage.type === 'error' ? 'text-red-500' : 'text-emerald-500'}`}>
                {addMessage.text}
              </p>
            )}
          </div>

          {incomingRequests.length > 0 && (
            <div className="space-y-2 mt-4 pb-2 border-b border-zinc-200 dark:border-zinc-800">
              <h4 className="font-bold text-sm text-amber-500">Incoming Requests</h4>
              {incomingRequests.map(req => (
                <div key={req.id} className="p-3 bg-amber-50 dark:bg-amber-950/20 rounded-xl flex items-center justify-between border border-amber-500/20">
                  <span className="font-bold cursor-default">{req.fromUsername}</span>
                  <div className="flex gap-2">
                    <Button size="sm" onClick={() => handleAcceptRequest(req.id)} className="h-7 text-xs bg-emerald-500 hover:bg-emerald-600 text-white rounded-lg">Accept</Button>
                    <Button size="sm" variant="outline" onClick={() => handleRejectRequest(req.id)} className="h-7 text-xs rounded-lg border-zinc-300 dark:border-zinc-700">Reject</Button>
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="space-y-2 mt-4">
            <h4 className="font-bold text-sm text-zinc-500">Your Contacts</h4>
            {sortedContacts.length === 0 ? (
              <p className="text-sm text-zinc-400 italic">No contacts added yet.</p>
            ) : (
              sortedContacts.map(c => (
                <div 
                  key={c.username} 
                  className={`p-3 bg-slate-50 dark:bg-zinc-900 rounded-xl flex items-center justify-between border transition-colors ${
                    c.online
                      ? 'border-emerald-500/20'
                      : 'border-zinc-200 dark:border-zinc-800 opacity-60'
                  }`}
                >
                  <span className="font-bold flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${c.online ? 'bg-emerald-500' : 'bg-zinc-400'}`}></span>
                    {c.username}
                  </span>
                  <span className="text-xs text-zinc-500">
                    {c.online ? `${c.sessionIds?.length || 0} session(s)` : 'Offline'}
                  </span>
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>

      {/* Groups */}
      <Card className="border-none shadow-xl bg-white dark:bg-[#111113] rounded-3xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Users className="text-primary" /> Mesh Groups</CardTitle>
          <CardDescription>Bundle contacts for WebRTC broadcasting.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-col gap-3">
            <Input 
              placeholder="New Group Name" 
              value={groupName} 
              onChange={e => setGroupName(e.target.value)} 
              className="bg-slate-50 dark:bg-zinc-900 rounded-xl"
            />
            
            <div className="flex flex-wrap gap-2">
              {contacts.map(c => (
                <button 
                  key={c.username}
                  onClick={() => toggleGroupSelection(c.username)}
                  className={`text-xs px-3 py-1.5 rounded-full font-bold transition-colors flex items-center gap-1 ${
                    selectedForGroup.includes(c.username)
                      ? 'bg-primary text-black'
                      : 'bg-slate-100 dark:bg-zinc-800 text-zinc-500'
                  }`}
                >
                  <span className={`w-1.5 h-1.5 rounded-full ${c.online ? 'bg-emerald-500' : 'bg-zinc-400'}`}></span>
                  {c.username}
                </button>
              ))}
            </div>

            <Button onClick={handleCreateGroup} disabled={!groupName || selectedForGroup.length === 0} className="rounded-xl w-full" variant="outline">
              <Plus className="w-4 h-4 mr-2" /> Create Group
            </Button>
          </div>

          <div className="space-y-2 mt-4">
            <h4 className="font-bold text-sm text-zinc-500">Your Groups</h4>
            {groups.length === 0 ? (
              <p className="text-sm text-zinc-400 italic">No groups defined yet.</p>
            ) : (
              groups.map(g => (
                <div key={g.id} className="p-3 bg-slate-50 dark:bg-zinc-900 rounded-xl border border-zinc-200 dark:border-zinc-800">
                  <span className="font-black text-primary">{g.name}</span>
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
