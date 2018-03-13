package br.ufs.dcomp.ChatRabbitMQ;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.rabbitmq.client.*;
import com.rabbitmq.http.client.Client;

import java.io.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.text.*;
import br.ufs.dcomp.ChatRabbitMQ.MessageProtoBuf.Mensagem;
import org.apache.commons.codec.binary.Base64;

public class ChatRabbitMQ {
    private static String toUser = "";
    public static String userG = "";
    private static boolean recebendo = false;
    public static String users[] = new String[10];
    public static String groups[] = new String[10];
    public static String groupMembers[] = new String[10];
    public static String queues[] = new String[10];
    public static String messagesQueueG = "";
    public static String filesQueueG = "";
    public static int numUsers = 0;
    public static int numGroups = 0;
    public static int numGroupMembers = 0;
    public static int numQueues = 0;
    public static boolean enviando_direto = false;
    public static boolean enviando_topico = false;
    public static boolean recebendo_arquivo = false;

    public static void main(String[] argv) throws Exception {

        ConnectionFactory factory = new ConnectionFactory();
        //factory.setUri("amqp://knnnagpk:-IDbljg1wv9R7QU117XFmEsxNJTXj_xc@elephant.rmq.cloudamqp.com/knnnagpk");
        factory.setUri("amqp://felipe:123@RMQLB-1925283795.us-west-2.elb.amazonaws.com");
        Connection connection = factory.newConnection();
        final Channel channel1 = connection.createChannel();
        final Channel channel2 = connection.createChannel();

        Scanner kb = new Scanner(System.in);

        System.out.print("User: ");
        String user = kb.nextLine();
        userG = user;
        ChatRabbitMQ.users[ChatRabbitMQ.numUsers++] = user;

        //channel.exchangeDeclare("", BuiltinExchangeType.FANOUT);
        String messagesQueue = user + "_messages";
        channel1.queueDeclare(messagesQueue, false, true, false, null);
        String filesQueue = user + "_files";
        channel2.queueDeclare(filesQueue, false, true, false, null);
        messagesQueueG = messagesQueue;
        filesQueueG = filesQueue;
        ChatRabbitMQ.queues[ChatRabbitMQ.numQueues++] = messagesQueue;

        System.out.print(">> ");

        Consumer consumer = new DefaultConsumer(channel1) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                String recMessage = new String(body, "UTF-8");
                //String sender = recMessage.substring(0, recMessage.indexOf('#'));
                if(recMessage.charAt(0) == '!') {
                    String command[] = (recMessage.substring(recMessage.lastIndexOf("!") + 1)).split(" ");
                    if(command[0].equals("toGroup")) {
                        groups[numGroups++] = command[1];
                        channel1.queueBind(messagesQueueG, command[1], "");
                        channel1.queueBind(filesQueueG, command[1], "");
                    }
                    else if(command[0].equals("delFromGroup")) {
                        channel1.queueUnbind(messagesQueueG, command[2], "");
                        channel1.queueUnbind(filesQueueG, command[2], "");
                        if(enviando_topico && toUser.equals(command[2]))
                        {
                            enviando_topico = enviando_direto = false;
                            toUser = "";
                            System.out.print("\n>> ");
                        }
                    }
                    else if(command[0].equals("removeGroup")) {
                        if(toUser.equals(command[1])) {
                            enviando_topico = enviando_direto = false;
                            toUser = "";
                            System.out.print("\n>> ");
                        }
                    }
                }
                else {
                    Mensagem rMes = Mensagem.parseFrom(body);
                    if(!rMes.getContent().hasName()) {
                        if(envelope.getExchange().equals("")) {
                            recMessage = "(" + rMes.getDate() + " às " + rMes.getTime() + ") " + rMes.getSender() + " diz: " + rMes.getContent().getBody().toStringUtf8();
                            System.out.println("\n" + recMessage);
                        }
                        else
                            if(!rMes.getSender().equals(userG)) {
                                recMessage = "(" + rMes.getDate() + " às " + rMes.getTime() + ") " + rMes.getSender() + "#" + rMes.getGroup() + " diz: " + rMes.getContent().getBody().toStringUtf8();
                                System.out.println("\n" + recMessage);
                            }
                    }
                    else {
                        if(!rMes.getSender().equals(userG)){
                            recMessage = "File \"" + rMes.getContent().getName() + "\" from @" + rMes.getSender() + " downloaded!";
                            // recMessage = "File \"" + rMes.getContent().getName().substring(rMes.getContent().getName().lastIndexOf('/'), rMes.getContent().getName().length()) + "\" from @" + rMes.getSender() + " downloaded!";

                            recebendo_arquivo = true;

                            File outFile = new File("/media/felipe/41ca39d0-2431-4452-ba07-1943d111b935/home/felipe/Documents/UFS/2017.2/Sistemas Distribuídos/IdeaProjects/ChatRabbitMQ/" + userG + "/" + rMes.getContent().getName());
                            outFile.getParentFile().mkdirs();
                            outFile.createNewFile();
                            FileOutputStream stream = new FileOutputStream(outFile);
                            //FileOutputStream stream = new FileOutputStream(rMes.getContent().getName());
                            try {
                                stream.write(rMes.getContent().getBody().toByteArray());
                            } finally {
                                stream.close();
                            }
                        }
                    }
                    if (enviando_direto && !toUser.equals(""))
                        System.out.print("@" + toUser + ">> ");
                    else if (enviando_topico && !toUser.equals("") && !(rMes.getSender().equals(userG)))
                        System.out.print("#" + toUser + ">> ");
                    else if(toUser.equals(""))
                        System.out.print(toUser + ">> ");
                    recebendo = true;
                }
            }
        };
        channel1.basicConsume(messagesQueue, true, consumer);

        Consumer consumer1 = new DefaultConsumer(channel2) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                String recMessage = new String(body, "UTF-8");
                //String sender = recMessage.substring(0, recMessage.indexOf('#'));
                Mensagem rMes = Mensagem.parseFrom(body);

                if(rMes.getContent().hasName() && !(rMes.getSender().equals(userG))) {
                    recMessage = "File \"" + rMes.getContent().getName() + "\" from @" + rMes.getSender() + " downloaded!";
                    // recMessage = "File \"" + rMes.getContent().getName().substring(rMes.getContent().getName().lastIndexOf('/'), rMes.getContent().getName().length()) + "\" from @" + rMes.getSender() + " downloaded!";

                    recebendo_arquivo = true;

                    File outFile = new File("/media/felipe/41ca39d0-2431-4452-ba07-1943d111b935/home/felipe/Documents/UFS/2017.2/Sistemas Distribuídos/IdeaProjects/ChatRabbitMQ/" + userG + "/" + rMes.getContent().getName());
                    outFile.getParentFile().mkdirs();
                    outFile.createNewFile();
                    FileOutputStream stream = new FileOutputStream(outFile);
                    //FileOutputStream stream = new FileOutputStream(rMes.getContent().getName());
                    try {
                        stream.write(rMes.getContent().getBody().toByteArray());
                    } finally {
                        stream.close();
                    }
                    System.out.println("\n" + recMessage);
                    if (enviando_direto && !toUser.equals(""))
                        System.out.print("@" + toUser + ">> ");
                    else if (enviando_topico && !toUser.equals(""))
                        System.out.print("#" + toUser + ">> ");
                    else
                        System.out.print(toUser + ">> ");
                    recebendo = true;
                }
            }
        };
        channel2.basicConsume(filesQueue, true, consumer1);

        String last = "";
        String command[];
        String group = "";
        String filePath = "";

        while(true) {
            String sentMessage = kb.nextLine();
            switch(sentMessage.charAt(0)) {
                case '@' :
                    last = "@";
                    toUser = sentMessage.substring(sentMessage.lastIndexOf("@") + 1);
                    enviando_direto = true;
                    enviando_topico = false;
                    break;
                case '!' :
                    last = "!";
                    command = (sentMessage.substring(sentMessage.lastIndexOf("!") + 1)).split(" ");
                    if(command[0].equals("newGroup")) {
                        groups[numGroups++] = command[1];
                        group = command[1];
                        channel1.exchangeDeclare(group, BuiltinExchangeType.FANOUT);
                        channel1.queueBind(messagesQueue, group, "");
                        channel1.queueBind(filesQueue, group, "");
                        groupMembers[numGroupMembers++] = user;
                    }
                    if(command[0].equals("toGroup")) {
                        group = command[1];
                        groupMembers[numGroupMembers++] = command[2];
                        channel1.basicPublish("", command[2] + "_messages", null, sentMessage.getBytes("UTF-8"));
                    }
                    if(command[0].equals("delFromGroup")) {
                        group = command[2];
                        channel1.basicPublish("", command[1] + "_messages", null, sentMessage.getBytes("UTF-8"));
                        //channel.queueUnbind(queueName, command[2], "");
                    }
                    if(command[0].equals("removeGroup")) {
                        group = command[1];
                        for(int i = 1; i < numGroupMembers; i++)
                            channel1.basicPublish("", groupMembers[i] + "_messages", null, sentMessage.getBytes("UTF-8"));
                        channel1.exchangeDelete(group);
                        //channel2.exchangeDelete(group);
                        if(toUser.equals(group)) {
                            enviando_topico = enviando_direto = false;
                            toUser = "";
                        }
                    }
                    if(command[0].equals("upload")) {
                        filePath = command[1];

                        if(command.length > 2) {
                            for(int i = 2; i < command.length; i++)
                                filePath = filePath + " " + command[i];
                        }

                        Arquivo arquivo = null;
                        if(enviando_direto)
                            arquivo = new Arquivo(user, filePath, enviando_direto, enviando_topico, toUser, channel2);
                        if(enviando_topico)
                            arquivo = new Arquivo(user, filePath, enviando_direto, enviando_topico, toUser, channel1);
                        Thread t = new Thread(arquivo);
                        t.start();
                    }
                    if(command[0].equals("listGroups")) {

                        String webPage = "http://RMQLB-1925283795.us-west-2.elb.amazonaws.com:15672/api/exchanges";
                        String username = "felipe";
                        String password = "123";

                        String authString = username + ":" + password;
                        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
                        String authStringEnc = new String(authEncBytes);

                        URL url = new URL(webPage);
                        URLConnection urlConnection = url.openConnection();
                        urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
                        InputStream is = urlConnection.getInputStream();
                        InputStreamReader isr = new InputStreamReader(is);

                        int numCharsRead;
                        char[] charArray = new char[1024];
                        StringBuffer sb = new StringBuffer();
                        while ((numCharsRead = isr.read(charArray)) > 0) {
                            sb.append(charArray, 0, numCharsRead);
                        }
                        String result = sb.toString();
                        String names = "";

                        String[] split = result.split("\"name\":\"");

                        for(int i=1; i<split.length; i++)
                        {
                            String nameRaw = split[i];
                            int index = nameRaw.indexOf("\"");
                            if (index > 0 && !nameRaw.startsWith("amq."))
                            {
                                String name = nameRaw.substring(0, index);
                                names = names + name + ", ";
                            }
                        }

                        if(!names.equals(""))
                            names = names.substring(0, names.lastIndexOf(','));
                        System.out.print(names + "\n");
                    }
                    if(command[0].equals("listUsers")) {

                        String webPage = "http://RMQLB-1925283795.us-west-2.elb.amazonaws.com:15672/api/queues";
                        String username = "felipe";
                        String password = "123";

                        String authString = username + ":" + password;
                        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
                        String authStringEnc = new String(authEncBytes);

                        URL url = new URL(webPage);
                        URLConnection urlConnection = url.openConnection();
                        urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
                        InputStream is = urlConnection.getInputStream();
                        InputStreamReader isr = new InputStreamReader(is);

                        int numCharsRead;
                        char[] charArray = new char[1024];
                        StringBuffer sb = new StringBuffer();
                        while ((numCharsRead = isr.read(charArray)) > 0) {
                            sb.append(charArray, 0, numCharsRead);
                        }
                        String result = sb.toString();
                        String names = "";

                        String[] split = result.split("\"name\":\"");

                        for(int i=1; i<split.length; i++)
                        {
                            String nameRaw = split[i];
                            int index = nameRaw.indexOf("\"");
                            if (index > 0 && !nameRaw.startsWith("amq.") && nameRaw.substring(nameRaw.indexOf('_') + 1, nameRaw.indexOf('_') + 6).equals("files"))
                            {
                                String name = nameRaw.substring(0, nameRaw.indexOf('_'));
                                names = names + name + ", ";
                            }
                        }

                        names = names.substring(0, names.lastIndexOf(','));
                        System.out.print(names + "\n");
                    }
                    break;
                case '#' :
                    last = "#";
                    toUser = sentMessage.substring(sentMessage.lastIndexOf("#") + 1);
                    enviando_topico = true;
                    enviando_direto = false;
                    break;
                default:
                    last = "";
                    break;
            }

            if(enviando_direto)
                System.out.print("@" + toUser + ">> ");
            else if(enviando_topico)
                System.out.print("#" + toUser + ">> ");
            else {
                System.out.print(">> ");
            }
            if(!last.equals("@") && !last.equals("!") && !last.equals("#")) {
                Date dNow = new Date( );
                SimpleDateFormat ft = new SimpleDateFormat ("dd/MM/yyyy HH:mm");
                String datetime[] = (ft.format(dNow)).split(" ");
                Mensagem.Builder message = Mensagem.newBuilder();
                message.setSender(user);
                message.setDate(datetime[0]);
                message.setTime(datetime[1]);
                Mensagem.Conteudo.Builder content = Mensagem.Conteudo.newBuilder();
                content.setType("text/plain");
                content.setBody(ByteString.copyFrom(sentMessage.getBytes("UTF-8")));
                message.setContent(content);
                if(enviando_direto) {
                    channel1.basicPublish("", toUser + "_messages", null, message.build().toByteArray());
                }
                else if(enviando_topico) {
                    message.setGroup(toUser);
                    channel1.basicPublish(toUser, "", null, message.build().toByteArray());
                }
            }
        }
    }
}