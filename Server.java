
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.zip.*;

public class Server {

    static final int SEGMENT_SIZE = 576;

    public static void main(String args[]) {
        CRC32 crc;
        String body;
        String ack;
        String writeToFile = "";
        int listenPort = 4119;
        String clientIp = "localhost";
        int ackPort = 9999;
        String headerLog = "stdout";

        // Exit program if incrorrect number of arguments
        if (args.length != 5) {
            System.out.println("Must Have 5 Arguments");
            System.exit(-1);
        } else {
            //5 arguments
            writeToFile = args[0];
            listenPort = Integer.parseInt(args[1]);
            clientIp = args[2];
            ackPort = Integer.parseInt(args[3]);
            headerLog = args[4];
        }
        // Exit program if invalid port numbers
        if ((listenPort < 1 | listenPort > 65535) || (ackPort < 1 | ackPort > 65535)) {
            System.out.println("Invalid port Number: (1-65535)");
            System.exit(-1);
        }

        try {

            // create segment size array;
            byte[] seg = new byte[SEGMENT_SIZE];
            DatagramPacket dpack = new DatagramPacket(seg, seg.length); // packet received
            DatagramSocket dsock = new DatagramSocket(listenPort); //socket for receiving packet

            PrintWriter fileWriter = new PrintWriter(writeToFile);// write to file
            PrintWriter logWriter = new PrintWriter(headerLog);// write to log
            while (true) {
                dsock.receive(dpack); // listen for packets
                // body of packet from byte[20] to byte[575]
                System.out.println("\nPacket Received from Sender! (Sequence #" + getSeqNum(seg) + ")");

                // if fin#=0, it's not last packet, if it is last packet, fin# = segment size
                if (getFinNum(seg) == 0) {
                    body = new String(seg, 20, 556);
                } else {
                    body = new String(seg, 20, getFinNum(seg));
                }
                
                // create checksum
                crc = new CRC32();
                crc.update(body.getBytes());
                ack = dpack.getAddress().getHostName() + ":" + crc.getValue();

                // Write to file
                fileWriter.print(body);
                logWriter.println(headerToString(Arrays.copyOfRange(seg, 0, 20)));
                if (headerLog.equals("stdout")) {
                    System.out.println("LOG LINE: " + headerToString(Arrays.copyOfRange(seg, 0, 20)));
                }


                // create ACK packet (Send straight to Client)
                DatagramPacket ackPacket = new DatagramPacket(ack.getBytes(),
                        ack.getBytes().length, InetAddress.getByName(clientIp), ackPort);
                dsock.send(ackPacket);
                System.out.println("Acknowledgment Sent! (ACK #" + getAckNum(seg) + ")");
                if (getFinNum(seg) != 0) {//Last packet received
                    System.out.println("\nDelivery Completed Successfully");
                    break;
                }

                // reset the length of the packet before reusing it.
                dpack.setLength(seg.length);
            }//end while
            fileWriter.close();
            logWriter.close();
            dsock.close();
        } catch (Exception e) {
            e.printStackTrace();
            //System.err.println(USAGE);
            //System.exit(-1);
        }
    }//end main

    //source, dest, seq, ack, fin, window, cksum, dataPointer
    public static String headerToString(byte[] h) {
        String sourcePort = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 0, 2))));
        String destPort = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 2, 4))));
        String seqNum = (Integer.toString(fourByteArrayToInt(Arrays.copyOfRange(h, 4, 8))));
        String ackNum = (Integer.toString(fourByteArrayToInt(Arrays.copyOfRange(h, 8, 12))));
        String fin = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 12, 14))));
        String winSize = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 14, 16))));
        String ckSum = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 16, 18))));
        //String dataPoint = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 18, 20))));
        
        return ("Source: " + sourcePort + ", Destination: " + destPort + ", Sequence #: " + seqNum + ", ACK #: " + ackNum +", fin #: "+ fin);
    }

    // method to convert byte array length=2 into an integer
    public static int twoByteArrayToInt(byte[] b) {
        return b[1] & 0xFF
                | (b[0] & 0xFF) << 8;
    }

    // method to convert byte array length=4 into an integer
    public static int fourByteArrayToInt(byte[] b) {
        return b[3] & 0xFF
                | (b[2] & 0xFF) << 8
                | (b[1] & 0xFF) << 16
                | (b[0] & 0xFF) << 24;
    }

    // method to grab fin number byte array and convert to integer
    public static int getFinNum(byte[] h) {
        int finNum = (twoByteArrayToInt(Arrays.copyOfRange(h, 12, 14)));
        return finNum;
    }

    //method to grab sequence number byte array and convert to integer
    public static int getSeqNum(byte[] h) {
        int seqNum = (fourByteArrayToInt(Arrays.copyOfRange(h, 4, 8)));
        return seqNum;
    }

    // method to get ack Number byte array and convert to int
    public static int getAckNum(byte[] h) {
        int ackNum = (fourByteArrayToInt(Arrays.copyOfRange(h, 8, 12)));
        return ackNum;
    }
    
}//end Server class