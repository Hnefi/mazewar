mazewar
=======

Implementation of Mazewar Distributed Game 

Completed by:
    Mark Sutherland     997332989
    Scott Whitty        997584378

*************
Running the server:
    sh server.sh <serverPort> <randomSeed>

- server.sh implements a DNS Lookup server used by Clients to find the entry points into the Ring
    - it also communicates the seed corresponding to the maze which is implemented in that Ring

Args:
    - serverPort: The port the server will listen on
    - randomSeed: A seed to pass to the Clients upon connection so they generate the same Maze, etc.

*************
Running the client:
    sh run.sh <serverHostName> <serverPort> <localClientPort>

- run.sh implements the Clients which, once connected to the Ring, require no communication from the DNS server until they leave the Ring

Args:
    - serverHostName: The Hostname of the DNS server
    - serverPort: The port that the DNS server is listening on; must be equal to the serverPort sent to server.sh
    - localClientPort: The port this Client listens on for new connections
        - if multiple Clients are run on the same machine, these ports must be different!

- players can dynamically join and drop the lobby in the middle of a game
    - join by starting a new client on a machine
    - drop by pressing Q
- players may spawn Robot clients into the maze up to a maximum of three per Maze
    - this is initiated by pressing R

- no strict limit in the number of players, but performance may degrade with larger numbers of clients
