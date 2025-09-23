import java.io.*;
import java.net.*;
import java.util.*;

public class SimpleServer implements Runnable {
    private static final int porta = 6060;
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final List<Group> groups = new ArrayList<>();
    private ServerSocket serverSocket;


    @Override
    public void run() {
        try {
            //Inicia o server e aguarda conexões 
            serverSocket = new ServerSocket(porta);
            System.out.println("Servidor aberto na porta " + porta);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + socket);

                //Cria um clienthandler para cada novo cliente
                ClientHandler client = new ClientHandler(socket);
                new Thread(client).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        } finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void broadcast(String message) {
        for (ClientHandler c : clients) {
            if (c != null) {
                c.sendText(message);
            }
        }
    }

    //Tratativa para mensagens privadas
    public static void privateMessage(String remetente, String destinatario, String message) {
        boolean found = false;
        for (ClientHandler c : clients) {
            if (c.getUsername() != null && c.getUsername().equalsIgnoreCase(destinatario)) {
                c.sendText("[Privado de " + remetente + "]: " + message);
                found = true;
                break;
            }
        }
        if (!found) {
            for (ClientHandler c : clients) {
                if (c.getUsername() != null && c.getUsername().equalsIgnoreCase(remetente)) {
                    c.sendText("Usuário '" + destinatario + "' não found.");
                    break;
                }
            }
        }
    }

    //Parte relacionada a manipulação de grupos
    private static class Group {
        String name;
        List<ClientHandler> members = new ArrayList<>();

        Group(String name) {
            this.name = name;
        }

        void addMember(ClientHandler c) {
            if (!members.contains(c)) {
                members.add(c);
            }
        }

        void removeMember(ClientHandler c) {
            members.remove(c);
        }

        void sendMessage(String message, ClientHandler sender) {
            for (ClientHandler m : members) {
                if (m != sender) {
                    m.sendText("[Grupo " + name + "] " + message);
                }
            }
        }

        void sendFile(String fileName, byte[] data, ClientHandler sender) {
            for (ClientHandler m : members) {
                if (m != sender) m.sendFile(fileName, data);
            }
        }
    }

    private static Group getGroup(String nome) {
        for (Group g : groups) {
            if (g.name.equalsIgnoreCase(nome)) {
                return g;
            }
        }
        return null;
    }

    private static void createGroup(String name, ClientHandler creator) {
        if (getGroup(name) != null) {
            creator.sendText("Grupo '" + name + "' já existe.");
            return;
        }
        Group g = new Group(name);
        g.addMember(creator);
        groups.add(g);
        creator.sendText("Grupo '" + name + "' criado e você entrou nele.");
    }

    private static void joinGroup(String name, ClientHandler user) {
        Group g = getGroup(name);
        if (g == null) {
            user.sendText("Grupo '" + name + "' não existe.");
            return;
        }
        g.addMember(user);
        user.sendText("Você entrou no grupo '" + name + "'.");
    }

    private static void leaveGroup(String name, ClientHandler user) {
        Group g = getGroup(name);
        if (g == null) {
            user.sendText("Grupo '" + name + "' não existe.");
            return;
        }
        g.removeMember(user);
        user.sendText("Você saiu do grupo '" + name + "'.");
    }

    private static void groupMessage(String name, String msg, ClientHandler sender) {
        Group g = getGroup(name);
        if (g == null) {
            sender.sendText("Grupo '" + name + "' não existe.");
            return;
        }
        if (!g.members.contains(sender)) {
            sender.sendText("Você não participa do grupo '" + name + "'.");
            return;
        }
        g.sendMessage("[" + sender.username + "]: " + msg, sender);
        sender.sendText("[Você em " + name + "]: " + msg);
    }


    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Getter para manter a integridade da variável
        public String getUsername() {
            return username;
        }

        public void run() {
            try {
                sendText("Digite seu nome de usuário:");
                while (true) {
                    String type = in.readUTF();
                    String name = in.readUTF();

                    if (name.trim().isEmpty()) {
                        sendText("Nome inválido. Tente novamente.");
                        continue;
                    }

                    //Tratativa para usuarios
                    boolean inUse = false;
                    for (ClientHandler c : clients) {
                        if (name.equalsIgnoreCase(c.username)) {
                            inUse = true;
                            break;
                        }
                    }

                    if (inUse) {
                        sendText("Nome já está em uso. Escolha outro.");
                    } else {
                        username = name;
                        clients.add(this);
                        broadcast(username + " entrou no chat.");
                        break;
                    }
                }

                //Tratativa para os comandos
                while (true) {
                    String type = in.readUTF();

                    if (type.equals("TEXT")) {
                        String msg = in.readUTF();

                        if (msg.equals("/quit")) {
                        sendText("Você saiu do chat.");
                        break;
                    }
                        if (msg.startsWith("/msg ")) {
                        String[] split = msg.split(" ",3);
                        if (split.length < 3) {
                            sendText("Comando inválido. Tente novamente.");
                        } else {
                            privateMessage(username, split[1], split[2]);
                        }

                        } else if (msg.startsWith("/create_group ")) {
                            String[] split = msg.split(" ",2);

                            if (split.length < 2) {
                            sendText("Comando inválido. Tente novamente.");

                            } else {
                            createGroup(split[1], this);
                        }

                        } else if (msg.startsWith("/join_group ")) {
                            String[] split = msg.split(" ",2);

                            if (split.length < 2) {
                            sendText("Comando inválido. Tente novamente.");

                            } else {
                            joinGroup(split[1], this);
                            }

                        } else if (msg.startsWith("/leave_group ")) {
                            String[] split = msg.split(" ",2);

                            if (split.length < 2) {
                            sendText("Comando inválido. Tente novamente.");

                            } else {
                            leaveGroup(split[1], this);
                            }

                        } else if (msg.startsWith("/gmsg ")) {
                            String[] split = msg.split(" ",3);

                            if (split.length < 3) {
                                sendText("Comando inválido. Tente novamente.");

                            } else {
                            groupMessage(split[1], split[2], this);
                            }

                        } else {
                            System.out.println("[" + username + "]: " + msg);
                            broadcast("[" + username + "]: " + msg);
                        }
                    } else if (type.equals("FILE")) {
                        //Leitura dos arquivos do client
                        String destino = in.readUTF();
                        boolean isGroup = in.readBoolean();
                        String fileName = in.readUTF();
                        long fileSize = in.readLong();

                        byte[] buffer = new byte[(int) fileSize];
                        in.readFully(buffer);

                        if (isGroup) {
                            Group g = getGroup(destino);
                            if (g == null) sendText("Grupo não existe.");
                            else g.sendFile(fileName, buffer, this);
                        } else {
                            boolean found = false;
                            for (ClientHandler c : clients) {
                                if (c.username.equalsIgnoreCase(destino)) {
                                    c.sendFile(fileName, buffer);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) sendText("Usuário não encontrado.");
                        }
                    }
                }

            } catch (IOException e) {
                System.out.println("Cliente desconectado: " + username);

            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                clients.remove(this);
                if (username != null) broadcast(username + " saiu do chat.");
            }
        }

        //Mensagens de texto
        public void sendText(String msg) {
            try {
                out.writeUTF("TEXT");
                out.writeUTF(msg);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //Envio de arquivos
        public void sendFile(String fileName, byte[] data) {
            try {
                out.writeUTF("FILE");
                out.writeUTF(fileName);
                out.writeLong(data.length);
                out.write(data);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SimpleServer server = new SimpleServer();
        server.run();
    }

}
