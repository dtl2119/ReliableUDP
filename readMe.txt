******************************************************************************
*
* Programming Assignment 2: Simple TCP-like transport-layer protocol
* CSEE W4119: Professor Vishal Misra
* Created By: Drew Limm (dtl2119)
*
******************************************************************************

My submission consists of 2 programs, Server.java (received packets) and
Client.java (Sends Packets).  To run my program, I created a make file for each
side, Server and Client.  Therefore, you can just run the command make in the src
directory of each program.  Here is one way you can run my program:


invoke proxy, then invoke server, then invoke Client with the following parameters

./newudpl -o localhost/4119 -i localhost/9999 -p5000:6000 -L50
java Server receive.txt 4119 localhost 9999 stdout
java Client test.txt localhost 5000 9999 1 stdout


My program has two classes: Server.java and Client.java.  Once executed, the 
Sever waits for a packet to arrive.  Client takes a file as an input parameter,
and divides this file into smaller segments assuming the file size is larger 
than the packet size.  Once the data for a packet has been divided, a 20 byte
header is attached to the front.  I defined the packet size to be 576 bytes.
Therefore, since the header is 20 bytes, the body size of each packet is 556
bytes.  

The 20 byte header consists of 8 components:
Bytes 0-2: Source Port #
Bytes 2-4: Destination Port #
Bytes 4-8: Sequence #
Bytes 8-12: ACK #
Bytes 12-14: flags
Bytes 14-16: Receive Windo
Bytes 16-18: CheckSum
Bytes 18-20: Urgent Data Pointer

For the fin, I decided to make all packets fin=0 except for the last packet.  The
last packet has fin = number of bytes in body of last packet.  This way, on the
server side, I can just check if fin=0, if not, then it is the last packet.
Also, for the RTT, I used a method that truncates decimal places so that all RTTs
have the same number of digits after the decimal.  


