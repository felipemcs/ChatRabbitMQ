package br.ufs.dcomp.ChatRabbitMQ;

import com.google.protobuf.ByteString;
import com.rabbitmq.client.Channel;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class Arquivo implements Runnable {
    private String user;
    private String filePath;
    private File file;
    private long byteLength;
    private byte[] fileContent;
    private FileInputStream fileInputStream;
    private boolean enviando_direto;
    private boolean enviando_topico;
    private String exName;
    private Channel channel;

    public Arquivo(String user, String filePath, boolean enviando_direto, boolean enviando_topico, String exName, Channel channel) throws Exception {
        this.user = user;
        this.filePath = filePath;
        this.file = file = new File(filePath);
        this.byteLength = file.length();
        this.fileContent = new byte[(int) byteLength];
        this.fileInputStream = new FileInputStream(file);
        fileInputStream.read(fileContent, 0, (int) byteLength);
        this.enviando_direto = enviando_direto;
        this.enviando_topico = enviando_topico;
        this.exName = exName;
        this.channel = channel;
    }

    public void run(){
        try{
            Scanner mimeFile = new Scanner (new File("/media/felipe/41ca39d0-2431-4452-ba07-1943d111b935/home/felipe/Documents/UFS/2017.2/Sistemas Distribuídos/IdeaProjects/ChatRabbitMQ/mime"));
            String extensions[] = new String[646];
            String mimeTypes[] = new String[646];
            int cont = 0;
            while (mimeFile.hasNextLine())
            {
                extensions[cont] = mimeFile.next();
                mimeTypes[cont] = mimeFile.next();
                cont++;
            }

            String ext = filePath.substring(filePath.lastIndexOf("."));
            File file = new File(filePath);

            Date dNow = new Date();
            SimpleDateFormat ft = new SimpleDateFormat ("dd/MM/yyyy HH:mm");
            String datetime[] = (ft.format(dNow)).split(" ");
            MessageProtoBuf.Mensagem.Builder message = MessageProtoBuf.Mensagem.newBuilder();
            message.setSender(user);
            message.setDate(datetime[0]);
            message.setTime(datetime[1]);
            MessageProtoBuf.Mensagem.Conteudo.Builder content = MessageProtoBuf.Mensagem.Conteudo.newBuilder();
            if(ext.equals(""))
                content.setType("text/plain");
            else
                for(int i = 0; i < 646; i++)
                    if(ext.equalsIgnoreCase(extensions[i])) {
                        content.setType(mimeTypes[i]);
                        break;
                    }
            content.setBody(ByteString.copyFrom(this.fileContent));
            content.setName(filePath.substring(filePath.lastIndexOf("/") + 1));
            message.setContent(content);
            if(this.enviando_direto)
                this.channel.basicPublish("direct_logs", this.exName, null, message.build().toByteArray());

            if(this.enviando_topico)
                channel.basicPublish(this.exName, "", null, message.build().toByteArray());

        } catch (Exception e){

        }
    }

}
