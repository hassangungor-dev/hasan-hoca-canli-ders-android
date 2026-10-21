const http = require("http");
const { WebSocketServer, WebSocket } = require("ws");

const rooms = new Map();
const port = Number(process.env.PORT || 10000);

const server = http.createServer((request, response) => {
  response.writeHead(200, { "content-type": "text/plain; charset=utf-8" });
  response.end("Hasan Hoca Canli Ders sunucusu calisiyor");
});

const wss = new WebSocketServer({ noServer: true, maxPayload: 64_000 });

server.on("upgrade", (request, socket, head) => {
  const url = new URL(request.url, `http://${request.headers.host}`);
  const match = url.pathname.match(/^\/room\/([A-Za-z0-9-]{4,40})$/);
  const participant = url.searchParams.get("participant");

  if (!match || !participant || participant.length > 80) {
    socket.write("HTTP/1.1 400 Bad Request\r\n\r\n");
    socket.destroy();
    return;
  }

  const room = match[1].toLowerCase();
  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit("connection", ws, { room, participant });
  });
});

wss.on("connection", (ws, { room, participant }) => {
  if (!rooms.has(room)) rooms.set(room, new Map());
  const peers = rooms.get(room);

  const previous = peers.get(participant);
  if (previous && previous.readyState === WebSocket.OPEN) previous.close(4000, "Tekrar baglandi");

  ws.room = room;
  ws.participant = participant;
  peers.set(participant, ws);

  ws.send(JSON.stringify({
    sender: "server",
    type: "peers",
    payload: { participants: [...peers.keys()].filter((id) => id !== participant) },
  }));
  broadcast(peers, { sender: "server", type: "joined", payload: { participant } }, participant);

  ws.on("message", (data, isBinary) => {
    if (isBinary || data.length > 64_000) return;
    let packet;
    try { packet = JSON.parse(data.toString()); } catch { return; }

    for (const [id, peer] of peers) {
      if (id === participant || peer.readyState !== WebSocket.OPEN) continue;
      if (!packet.target || packet.target === id) peer.send(data.toString());
    }
  });

  ws.on("close", () => {
    if (peers.get(participant) !== ws) return;
    peers.delete(participant);
    broadcast(peers, { sender: "server", type: "left", payload: { participant } });
    if (peers.size === 0) rooms.delete(room);
  });
});

function broadcast(peers, packet, excludedId) {
  const text = JSON.stringify(packet);
  for (const [id, peer] of peers) {
    if (id !== excludedId && peer.readyState === WebSocket.OPEN) peer.send(text);
  }
}

server.listen(port, "0.0.0.0", () => {
  console.log(`Signaling server listening on ${port}`);
});
