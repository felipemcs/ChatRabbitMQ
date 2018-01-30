package br.ufs.dcomp.ChatRabbitMQ;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.rabbitmq.client.*;

import java.io.*;
import java.io.IOException;
import java.util.*;
import java.text.*;
import br.ufs.dcomp.ChatRabbitMQ.MessageProtoBuf.Mensagem;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.java2d.loops.GraphicsPrimitive;

import javax.print.DocFlavor;

public class ChatRabbitMQ {
    private static String toUser = "";
    public static String userG = "";
    private static boolean recebendo = false;
    public static String users[] = new String[10];
    public static String groups[] = new String[10];
    public static String groupMembers[] = new String[10];
    public static String queues[] = new String[10];
    public static String queueNameG = "";
    public static int numUsers = 0;
    public static int numGroups = 0;
    public static int numGroupMembers = 0;
    public static int numQueues = 0;
    public static boolean enviando_direto = false;
    public static boolean enviando_topico = false;

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri("amqp://knnnagpk:-IDbljg1wv9R7QU117XFmEsxNJTXj_xc@elephant.rmq.cloudamqp.com/knnnagpk");
        Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();

        Scanner kb = new Scanner(System.in);

        System.out.print("User: ");
        String user = kb.nextLine();
        userG = user;
        //int userNum = numUsers++;
        ChatRabbitMQ.users[ChatRabbitMQ.numUsers++] = user;
        System.out.println(ChatRabbitMQ.numUsers);

        channel.exchangeDeclare("direct_logs", BuiltinExchangeType.DIRECT);
        String queueName = channel.queueDeclare().getQueue();
        queueNameG = queueName;
        ChatRabbitMQ.queues[ChatRabbitMQ.numQueues++] = queueName;

        channel.queueBind(queueName, "direct_logs", user);

        // channel.queueBind(queueName, user, "");

        System.out.print(">> ");

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                String recMessage = new String(body, "UTF-8");
                //String sender = recMessage.substring(0, recMessage.indexOf('#'));
                if(recMessage.charAt(0) == '!') {
                    String command[] = (recMessage.substring(recMessage.lastIndexOf("!") + 1)).split(" ");
                    if(command[0].equals("toGroup")) {
                        groups[numGroups++] = command[1];
                        channel.queueBind(queueNameG, command[1], "");
                    }
                    else if(command[0].equals("delFromGroup"))
                        channel.queueUnbind(queueNameG, command[2], "");
                }
                else {
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
        channel.basicConsume(queueName, true, consumer);

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
                        channel.exchangeDeclare(group, BuiltinExchangeType.FANOUT);
                        channel.queueBind(queueName, group, "");
                        //toUser = sentMessage.substring(sentMessage.lastIndexOf(" ") + 1);
                        groupMembers[numGroupMembers++] = user;
                    }
                    if(command[0].equals("toGroup")) {
                        group = command[1];
                        groupMembers[numGroupMembers++] = command[2];
                        channel.basicPublish("direct_logs", command[2], null, sentMessage.getBytes("UTF-8"));
                        channel.queueBind(queueName, command[1], "");
                    }
                    if(command[0].equals("delFromGroup")) {
                        group = command[2];
                        channel.basicPublish("direct_logs", command[1], null, sentMessage.getBytes("UTF-8"));
                        //channel.queueUnbind(queueName, command[2], "");
                    }
                    if(command[0].equals("removeGroup")) {
                        group = command[1];
                        channel.exchangeDelete(group);
                    }
                    if(command[0].equals("upload")) {
                        filePath = command[1];
                        String ext = "";

                        if(command.length > 2) {
                            for(int i = 2; i < command.length; i++)
                                filePath = filePath + " " + command[i];
                        }

                        Arquivo arquivo = new Arquivo(user, filePath,enviando_direto, enviando_topico, toUser, channel);
                        Thread t = new Thread(arquivo);
                        t.start();

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

            //String message = kb.nextLine();

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
                if(enviando_direto) {
                    sentMessage = "(" + message.getDate() + " às " + message.getTime() + ") " + message.getSender() + " diz: " + sentMessage;
                    channel.basicPublish("direct_logs", toUser, null, sentMessage.getBytes("UTF-8"));
                }
                else if(enviando_topico) {
                    message.setGroup(toUser);
                    sentMessage = "(" + message.getDate() + " às " + message.getTime() + ") " + message.getSender() + "#" + toUser + " diz: " + sentMessage;
                    channel.basicPublish(toUser, "", null, sentMessage.getBytes("UTF-8"));
                }
            }
            //System.out.println(" [x] Sent '" + message + "'");
        }
    }
}