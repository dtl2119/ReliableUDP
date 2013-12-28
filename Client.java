
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

public class Client {

    public static void main(String args[]) {
        int segSize = 576; //each packet is 576 bytes
        int headerSize = 20;
        int bodySize = segSize - headerSize;
        int retry_count = 0; //each packet
        int total_segs_retransmitted = 0; //total for file
        int total_bytes_sent = 0; //keep track of total bytes sent (include retransmitted)
        long timeSent;// to evaluate RTT
        long timeReceived;// to evaluate RTT
        byte[] body;
        String fileToSend = "";

        try {
            // Handle incorrect number of arguments
            if (args.length != 6) {
                System.out.println("Must Have 6 Arguments");
                System.exit(-1);
            }

            // 6 command-line arguments
            fileToSend = args[0];
            InetAddress serverIp = InetAddress.getByName(args[1]);
            int remotePort = Integer.parseInt(args[2]);
            int ackPort = Integer.parseInt(args[3]); //to receive ACKs
            int windowSize = Integer.parseInt(args[4]);
            String logFile = args[5];

            // Exit program if invalid port number
            if ((remotePort < 1 | remotePort > 65535) || (ackPort < 1 | ackPort > 65535)) {
                System.out.println("Invalid port Number: (1-65535)");
                System.exit(-1);
            }

            int numOfPacks;
            File f = new File(fileToSend);
            int len = (int) f.length(); // determine file size
            numOfPacks = (len / bodySize) + 1; //determine number of packets needed
            FileInputStream in = new FileInputStream(f);
            byte[] byteArrayWholeFile = new byte[len];
            in.read(byteArrayWholeFile);
            PrintWriter logWriter = new PrintWriter(logFile);
            int lastPackBodyLength = 1;


            int bytesRead = 0, n;
            logWriter = new PrintWriter(logFile);
            for (int i = 0; i < numOfPacks; i++) {
                if (i != numOfPacks - 1) {// not last packet
                    body = new byte[bodySize]; // create a byte array of len=BODY_SIZE
                    body = divideArray(byteArrayWholeFile, bytesRead, bytesRead + bodySize);
                    bytesRead = bytesRead + bodySize;
                } else {// if last packet, adjust packet size
                    lastPackBodyLength = len % bytesRead;
                    body = new byte[lastPackBodyLength];
                    body = divideArray(byteArrayWholeFile, bytesRead, bytesRead + (lastPackBodyLength));
                    bytesRead = bytesRead + (lastPackBodyLength);
                }


                //create header (seq and ack # start at 0 and increment by 1)
                byte[] header;
                CRC32 checkSum = new CRC32();
                int checkSumValue = (int) checkSum.getValue();
                if (i == numOfPacks - 1) {
                    // if it's the last packet, set fin equal to size of body of last packet
                    header = createHeader(ackPort, remotePort, i, i, lastPackBodyLength, windowSize, checkSumValue, 0);
                } else {
                    header = createHeader(ackPort, remotePort, i, i, 0, windowSize, checkSumValue, 0);
                }
                //source, dest, seq, ack, fin, window, cksum, dataPointer

                byte[] wholePacket = combineTwoByteArrays(header, body);
                retry_count = 0;//reset retry count to 0 for each packet
                byte[] ack = new byte[segSize];
                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
                DatagramSocket ackSocket = new DatagramSocket(ackPort);

                timeSent = System.nanoTime();// outside while to include retransmission time
                while (true) {
                    // set acknowledgment delay tolerance (in milliseconds)
                    ackSocket.setSoTimeout(1000);
                    DatagramPacket packet = new DatagramPacket(wholePacket,
                            wholePacket.length, serverIp, remotePort);
                    ackSocket.send(packet);// send packet
                    total_bytes_sent += wholePacket.length;
                    System.out.println("\nPacket Sent! (Sequence #" + getSeqNum(wholePacket) + ")" + "\n(Wait for ACK" + ")");


                    try {
                        ackSocket.receive(ackPacket); // wait for acknowledgment
                        timeReceived = System.nanoTime();
                        String ackString = new String(ack, 0, ackPacket.getLength());
                        String logLine = headerToString(header) + " Estimated RTT: " + truncateDouble(((timeReceived - timeSent) * 0.000001), 3) + " ms";
                        logWriter.println(logLine);
                        if (logFile.equals("stdout")) {// if stdout, print to System
                            System.out.println("LOG LINE: " + logLine);
                        }
                        System.out.println("Acknowledgment Received (ACK #" + getAckNum(wholePacket) + ")");
                        System.out.println("Number of unsuccessful transmissions for this packet: " + retry_count);
                        System.out.println();
                        ackSocket.close();
                        if (i == numOfPacks - 1) {//last ACK received, delivery completed
                            System.out.println("\nDelivery Completed Successfully");
                            System.out.println("Total File Size: " + len + " Bytes");
                            System.out.println("Total bytes attempted " + total_bytes_sent);
                            System.out.println("Segments Sent: " + numOfPacks);
                            System.out.println("Total Segments Retransmitted: " + total_segs_retransmitted);
                            System.out.println();
                        }
                        break;
                    } catch (InterruptedIOException e) { // acknowledgment timeout
                        retry_count++;// retransmit count per packet
                        total_segs_retransmitted++;// total retransmit count
                    }
                }// end while
            }//end for
            in.close();
            logWriter.close();
        } catch (FileNotFoundException e) {
            System.out.println("File " + fileToSend + " not Found!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to convert integer to byte array length=2
    public static byte[] intTo2ByteArray(int a) {
        byte[] arr = new byte[2];
        arr[1] = (byte) (a & 0xFF);
        arr[0] = (byte) ((a >> 8) & 0xFF);
        return arr;
    }

    // Method to convert integer to byte array length=4
    public static byte[] intTo4ByteArray(int a) {
        byte[] arr = new byte[4];
        arr[3] = (byte) (a & 0xFF);
        arr[2] = (byte) ((a >> 8) & 0xFF);
        arr[1] = (byte) ((a >> 16) & 0xFF);
        arr[0] = (byte) ((a >> 24) & 0xFF);
        return arr;
    }

    // method to convert byte array length 2 to integer
    public static int twoByteArrayToInt(byte[] b) {
        return b[1] & 0xFF
                | (b[0] & 0xFF) << 8;
    }

    // method to convert byte array length 4 to integer
    public static int fourByteArrayToInt(byte[] b) {
        return b[3] & 0xFF
                | (b[2] & 0xFF) << 8
                | (b[1] & 0xFF) << 16
                | (b[0] & 0xFF) << 24;
    }

    // returns 20 byte header array
    public static byte[] createHeader(int source, int dest, int seq, int ack, int fin, int window, int cksum, int dataPointer) {
        byte[] srcPortArr = intTo2ByteArray(source);
        byte[] destPortArr = intTo2ByteArray(dest);
        byte[] seqArr = intTo4ByteArray(seq);
        byte[] ackNumberArr = intTo4ByteArray(ack);
        byte[] finArr = intTo2ByteArray(fin);
        byte[] windowArr = intTo2ByteArray(window);
        byte[] cksumArr = intTo2ByteArray(cksum);
        byte[] dataPointerArr = intTo2ByteArray(dataPointer);


        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] arr = new byte[20];
        try {
            outputStream.write(srcPortArr);
            outputStream.write(destPortArr);
            outputStream.write(seqArr);
            outputStream.write(ackNumberArr);
            outputStream.write(finArr);
            outputStream.write(windowArr);
            outputStream.write(cksumArr);
            outputStream.write(dataPointerArr);
            arr = outputStream.toByteArray();
        } catch (IOException e) {
            e.getMessage();
        }
        return arr;
    }

    // method to combine two byte arrays of length 2 each
    public static byte[] combineTwoByteArrays(byte[] first, byte[] second) {
        byte[] combined = new byte[first.length + second.length];

        for (int i = 0; i < combined.length; ++i) {
            combined[i] = i < first.length ? first[i] : second[i - first.length];
        }
        return combined;
    }

    // convert 20 byte header array to String
    public static String headerToString(byte[] h) {
        String sourcePort = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 0, 2))));
        String destPort = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 2, 4))));
        String seqNum = (Integer.toString(fourByteArrayToInt(Arrays.copyOfRange(h, 4, 8))));
        String ackNum = (Integer.toString(fourByteArrayToInt(Arrays.copyOfRange(h, 8, 12))));
        String fin = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 12, 14))));
        String winSize = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 14, 16))));
        String ckSum = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 16, 18))));
        //String dataPoint = (Integer.toString(twoByteArrayToInt(Arrays.copyOfRange(h, 18, 20))));
        
        return ("Source: " + sourcePort + ", Destination: " + destPort + ", Sequence #: " + seqNum + ", ACK #: " + ackNum +", fin #: "+ fin + ", ");

    }

    //divide byte array from start index(inclusive) to end index(exclusive)
    public static byte[] divideArray(byte[] wholeArray, int start, int end) {
        byte[] arr = new byte[end - start];
        arr = Arrays.copyOfRange(wholeArray, start, end);
        return arr;
    }

    // return seq num byte array as integer
    public static int getSeqNum(byte[] h) {

        int seqNum = (fourByteArrayToInt(Arrays.copyOfRange(h, 4, 8)));
        return seqNum;

    }

    // return ack num byte array as integer
    public static int getAckNum(byte[] h) {
        int ackNum = (fourByteArrayToInt(Arrays.copyOfRange(h, 8, 12)));
        return ackNum;
    }

    // to print RTT only to certain # of decimal places
    public static double truncateDouble(double n, int numDigits) {
        double result = n;
        String aswr = "" + n;
        int i = aswr.indexOf('.');
        if (i != -1) {
            if (aswr.length() > i + numDigits) {
                aswr = aswr.substring(0, i + numDigits + 1);
                result = Double.parseDouble(aswr);
            }
        }
        return result;
    }
}
