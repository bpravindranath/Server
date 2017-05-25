# Server
 A Server that communicates with a Client over TCP and UDP. 
 This Server waits for a TCP connection with a Client.
 To get the HTTP Get Request File, the Client will initialize three variables: the packet size, time limit, and website and send values to the Server.
 Then over UDP, the Server sends a file containing a HTTP Get Request to the Client.
 After sending files, the connection is closed and the Server goes back to waiting for a connection with another Client...
