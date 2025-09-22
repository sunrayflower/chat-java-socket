import java.io.*;
import java.net.Socket;

public class Client {
    private DataOutputStream dos;
    private DataInputStream dis;

    public void run() {
        try (Socket client = new Socket("localhost", 666)){
            dos = new DataOutputStream(client.getOutputStream());
            dis = new DataInputStream(client.getInputStream());

            Thread t = new Thread(this::listen);
            t.start();

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            String msg;
            while ((msg = console.readLine()) != null) {
                if (msg.equals("/quit")) {
                    sendText(msg);
                    break;
                }

                if (msg.startsWith("/send_file ")) {
                    String[] parts = msg.split(" ", 3);
                    if (parts.length < 3) {
                        System.out.println("Comando inválido! Tente novamente.");
                        continue;
                    }
                    sendFile(parts[1], false, parts[2]);
                } else if (msg.startsWith("/send_gfile ")) {
                    String[] parts = msg.split(" ", 3);
                    if (parts.length < 3) {
                        System.out.println("Comando inválido! Tente novamente.");
                        continue;
                    }
                    sendFile(parts[1], true, parts[2]);
                } else {
                    sendText(msg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendText(String message) throws IOException {
        dos.writeUTF("TEXT");
        dos.writeUTF(message);
        dos.flush();
    }

    private void sendFile(String destino, boolean isGroup, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("Arquivo não encontrado!");
            return;
        }

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[(int) file.length()];
        fis.read(buffer);
        fis.close();

        dos.writeUTF("FILE");
        dos.writeUTF(destino);
        dos.writeBoolean(isGroup);
        dos.writeUTF(file.getName());
        dos.writeLong(buffer.length);
        dos.write(buffer);
        dos.flush();

        System.out.println("Arquivo enviado: " + file.getName());
    }

    private void listen() {
        try {
            while (true) {
                String type = dis.readUTF();
                if (type.equals("TEXT")) {
                    System.out.println(dis.readUTF());
                } else if (type.equals("FILE")) {
                    String fileName = dis.readUTF();
                    long size = dis.readLong();
                    byte[] buffer = new byte[(int) size];
                    dis.readFully(buffer);

                    File outFile = new File("download_" + fileName);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    fos.write(buffer);
                    fos.close();

                    System.out.println("Arquivo recebido: " + outFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            System.out.println("Conexão encerrada.");
        }
    }

    public static void main(String[] args) {
        new Client().run();
    }
}