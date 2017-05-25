/**
 * Created by Barnabas_Ravindranath on 5/7/17.
 */

import java.net.*;
import java.io.*;
import java.lang.String;
import java.util.ArrayList;
import java.util.concurrent.*;

public class RavindranathServer   {

    private final static int port = 23251;
    private static final int udpPort = 23152;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(5);
    private static final String filename = "HTML.txt";

    private ServerSocket serverSocket;
    private Socket clientsocket;

    private String url = "";
    private String html_file;
    private String packets_recieved;
    private String received = " ";

    private byte packet_size = 0;

    private int time_limit = 0;

    //main thread method
    public static void main(String[] args) throws IOException, InterruptedException {
        new RavindranathServer().go(); //Start Server
    }

    //Main method for Server to handle Clients
    public void go() throws InterruptedException {

        try {
            serverSocket = new ServerSocket(port);

            while (true) { //main loop for server

                //Waiting for a Connection
                System.out.println("Waiting for connection...");
                clientsocket = serverSocket.accept();

                //Recieved a Connection from Client
                System.out.println("Connection from " + clientsocket.getInetAddress().getHostName());

                //make an output stream and send message to Client
                PrintWriter toClient = new PrintWriter(clientsocket.getOutputStream(), true);

                //sends message to client
                sendtoclient(toClient);

                //thread used to read from client
                startreading(clientsocket);


                //Sends Get Request based on Clients Web address
                SendHTTPRequest();

                //sends page bytes and number of total packets
                sendPageBytes(toClient);

                //reads for Client's "Size Ok!" reply
                startreading(clientsocket);

                //If "Size Ok!", server starts sending UDP packets
                if(received.compareTo("Size OK!") == 0){
                    threadPool.submit(new Transfer_File(udpPort, packet_size, time_limit));
                    threadPool.awaitTermination(15,TimeUnit.SECONDS);
                    System.out.println("Transfer Completed waiting on Client response...");
                }

                //if "Page Ok", then we have successfully sent all UDP packets
                if(packets_recieved.compareTo("Page Ok!") == 0){
                    System.out.println("All UDP packets arrived successfully");
                    System.out.println(clientsocket.getInetAddress() + " Ok!");
                }
                else { System.out.println("Transfer again"); } //transfer again

                System.out.println("Connection from localhost is now closed");
            }

        } catch (IOException e) {
            System.err.println(e);
        }

    }

    //sends greeting message to Client
    public void sendtoclient(PrintWriter toClient){
            System.out.println("Sending a greeting message to Client\n");
            toClient.print('S');
            toClient.flush();
            toClient.println("Hello this is the Server at TCP port " + port);
            toClient.flush();
    }

    //sends total page bytes and total udp packets that will be sent to client
    public void sendPageBytes(PrintWriter toClient){
        toClient.print('M');
        toClient.flush();
        toClient.println("Sending HTML page size in bytes");
        toClient.print('P');
        toClient.flush();
        toClient.println(html_file.getBytes().length); //total page bytes
        toClient.flush();
        toClient.print('C');
        toClient.flush();
        toClient.println((int) Math.floor(html_file.getBytes().length / packet_size) + 1 ); //total packets
        toClient.flush();
        System.out.println("Sent Page Bytes and Total Packets to Client\n");
    }

    //Thread handler to get Read Data from ClientHandler
    public void startreading(Socket client) throws InterruptedException{
        threadPool.submit(new ClientHandler(client));
        threadPool.awaitTermination(5, TimeUnit.SECONDS);
    }

    public class ClientHandler implements Runnable {

        BufferedReader fromClient;
        Socket sock;

        public ClientHandler(Socket clientsocket) {
            try {
                sock = clientsocket;
                InputStreamReader reader = new InputStreamReader(sock.getInputStream());
                fromClient = new BufferedReader(reader);

            } catch (Exception e) {
                System.err.println(e);
            }
        }

        public void run() {

            try {
                while (fromClient.ready()) {

                    int message = fromClient.read();
                    switch (message) {
                        case 'S': //Web Url (String)
                            url = fromClient.readLine().trim();
                            System.out.println("Client: The Web server name is  " + url);
                            break;

                        case 'P': //Packet (int)
                            int x = Integer.parseInt((fromClient.readLine().trim()));
                            packet_size = (byte) x;
                            System.out.println("Client: The Size of the Packet is " + packet_size);
                            break;
                        case 'K': //ACK (String)
                            received = fromClient.readLine().trim();
                            System.out.println("Client: " + received);
                            break;

                        case 'T': //Time-limit (int)
                            time_limit = Integer.parseInt((fromClient.readLine().trim()));
                            System.out.println("Client: The Time-Limit is " + time_limit);
                            break;
                        default:
                            System.out.println("Error in TCP Packet Message");
                            break;

                    }
                }
            } catch (Exception e) {
                System.err.println(e);
            }
        }
    }

    //Thread handler to get HTTP Request
    public void SendHTTPRequest() throws InterruptedException {
        threadPool.submit(new HTTP_Request());
        threadPool.awaitTermination(5, TimeUnit.SECONDS);
    }

    //class to get the HTTP request from the given Web Server submitted from Client
    public class HTTP_Request implements Runnable  {

        //Method to get the request and put the string into a File
        public String getHTML(String urlToRead) throws Exception {

            StringBuilder result = new StringBuilder();

            URL url = new URL(urlToRead);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            System.out.println("Receiving get request from Web Server "+ url);
            System.out.println("Response code from Web Server: "+ responseCode);


            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;


            while ((line = rd.readLine()) != null) {
                result.append(line);
            }

            while (rd.ready()) {
                result.append(rd.readLine());
            }

            rd.close();

            html_file = result.toString();

            try(  PrintWriter out = new PrintWriter(filename)){
                out.println(html_file);
            }
            return result.toString();
        }

        //thread handler to get HTTP Get Request
        @Override
        public void run() {
           String urlstring = "http://" + url;
            try {
               System.out.println(getHTML(urlstring));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //class to transfer page bytes with file to client
    public class Transfer_File implements Runnable  {
        private int Byte_info_starts = 2;
        private String file_name = filename;
        private File file = new File(file_name);
        private String host = "localhost";
        private DatagramSocket udpSocketServer;
        private int clientPort;
        private byte packetSize;
        private byte[] fileData;
        private int packetNumber = 0;
        private int time_limit = 0;
        private int fileBytePointer = 0;
        private ExecutorService sendingThread;
        Future<String> myStuff;

        //constructor
        public Transfer_File(int port_udp, byte packetSize, int timeout) throws IOException {
           SetUpConnection(port_udp, packetSize, timeout);
       }

        //initialize variables and establishes connection
       private void SetUpConnection(int port_udp, byte packetSize, int timeout) throws IOException {

           this.clientPort = port_udp;
           this.packetSize = packetSize;
           this.time_limit = timeout;

           udpSocketServer = new DatagramSocket();

           FileInputStream input = new FileInputStream(file);

           fileData = new byte[(int) file.length()];

           input.read(fileData,0,fileData.length);
       }

        @Override
        public void run() { //thread handler to send page bytes to client
            try {

                System.out.println("Sending Packets...");

                startSending(); //Callable method that starts the sending of UDP packets

                checkforResend(); //checks to see if time_limit was exceeded and prompts a resend.

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //function to start resend and listen for client "Page Ok!" response
        private void startSending() throws IOException {
            sendingThread = Executors.newSingleThreadExecutor();
            myStuff = sendingThread.submit(new Callable<String>() {
                @Override
                public String call() throws IOException, InterruptedException {
                    Send_Page_Packets(); //sends all UDP packets to client
                    while(true) {
                        listenForAck(); //listens for UDP client response
                    }
                }
            });
        }

        //Creates the UDP packet according to packet size and then sends them over UDP
       private void Send_Page_Packets() throws IOException{
           packetNumber = 1;

           while (fileBytePointer < fileData.length) {
               byte[] temporary = getpacketData();

               DatagramPacket sendPacket = new DatagramPacket(temporary, temporary.length, InetAddress.getByName(host), clientPort);

               udpSocketServer.send(sendPacket);

               System.out.println("Sent packet:" + packetNumber);

               packetNumber += 1;
           }
       }

       //Assigns packet numnber and puts HTML page data into Packet
       private byte [] getpacketData(){
           byte[] temporary = new byte[packetSize + 2];
           temporary[0] = (byte) packetNumber;
           temporary[1] = (byte) file.length();


           for (int i = Byte_info_starts; i < temporary.length; i++) { // dfh = 2
               temporary[i] = fileData[fileBytePointer];
               fileBytePointer++;
               if (fileBytePointer >= fileData.length) {
                   break;
               }
           }
           return temporary;
       }

       //listens for Client response of getting all the packets over UDP
       private void listenForAck() throws IOException, InterruptedException {

           DatagramPacket ackPacket = new DatagramPacket(new byte[8], 8);
           udpSocketServer.receive(ackPacket);
           System.out.println("Received ACk");
           packets_recieved  = new String(ackPacket.getData());;

       }

       //Checks to see all the packets were sent and received in the time limit given by client
       private void checkforResend() { //will resend
            try {
                String ack = myStuff.get(time_limit,TimeUnit.MILLISECONDS); // never finished in time always fails to timeout exception
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                time_limit = time_limit * 2;
                myStuff.cancel(true);
            }
            sendingThread.shutdown();
        }
    }
}
