Project: Medieval MMORPG

Architecture:
- Client sends packets to server
- Server processes and returns updates

Client:
- Handles rendering, input, UI
- Sends movement, chat, actions

Server:
- Handles game logic, player state, world updates
- Validates all client actions

Networking:
- Packet-based communication
- MovementPacket → updates player position
- ChatPacket → broadcasts message