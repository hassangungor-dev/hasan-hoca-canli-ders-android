import { DurableObject } from "cloudflare:workers";

export interface Env {
  ROOMS: DurableObjectNamespace<Room>;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const match = url.pathname.match(/^\/room\/([A-Za-z0-9-]{4,40})$/);
    if (!match) return new Response("Oda bulunamadı", { status: 404 });
    const id = env.ROOMS.idFromName(match[1].toLowerCase());
    return env.ROOMS.get(id).fetch(request);
  },
};

export class Room extends DurableObject<Env> {
  async fetch(request: Request): Promise<Response> {
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("WebSocket gerekli", { status: 426 });
    }
    const participant = new URL(request.url).searchParams.get("participant");
    if (!participant) return new Response("Katılımcı kimliği gerekli", { status: 400 });
    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    this.ctx.acceptWebSocket(server, [participant]);
    this.broadcast({ sender: "server", type: "joined", payload: { participant } }, server);
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer) {
    if (typeof message !== "string" || message.length > 64_000) return;
    let packet: { target?: string };
    try { packet = JSON.parse(message); } catch { return; }
    for (const peer of this.ctx.getWebSockets()) {
      if (peer === socket) continue;
      const id = this.ctx.getTags(peer)[0];
      if (!packet.target || packet.target === id) peer.send(message);
    }
  }

  async webSocketClose(socket: WebSocket) {
    const participant = this.ctx.getTags(socket)[0];
    this.broadcast({ sender: "server", type: "left", payload: { participant } }, socket);
  }

  private broadcast(packet: unknown, excluded?: WebSocket) {
    const text = JSON.stringify(packet);
    for (const peer of this.ctx.getWebSockets()) {
      if (peer !== excluded) peer.send(text);
    }
  }
}
