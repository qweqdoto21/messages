package proto;

import com.google.protobuf.Message;
import io.netty.channel.MessageSizeEstimator;
import proto.MessagesProto;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import proto.MessagesGrpc;
import proto.MessagesProto.PostMessageResponse;
import proto.MessagesProto.PostMessageRequest;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import io.grpc.Status;


public class App {
    private final int port = 8000; // Вы можете выбрать любой доступный порт
    private Server server;

    public static void main(String[] args) throws Exception {
        final App server = new App();
        String url = "jdbc:postgresql://localhost:5432/";
        String user = "postgres";
        String password = "dildo";
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            if (conn != null) {
                System.out.println("Вечер в хату!");
                server.start(conn);
                server.blockUntilShutdown();
            }
        } catch (SQLException e) {
            System.out.println("Я сидел петухом на зоне, т.к.: " + e.getMessage());
        }


    }

    private void start(Connection db) throws Exception {
        MessagesFapus gptsultan = new MessagesFapus();
        gptsultan.db = db;
        server = ServerBuilder.forPort(port)
                .addService(gptsultan)
                .build()
                .start();
        System.out.println("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            App.this.stop();
            System.err.println("*** server shut down");
        }));
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    static class MessagesFapus extends MessagesGrpc.MessagesImplBase {
        Connection db;


        @Override
        public void deleteMessage(MessagesProto.DeleteMessageRequest request, StreamObserver<MessagesProto.DeleteMessageResponse> responseObserver) {
            String sql = "DELETE FROM messages WHERE id = ?;";
            try (PreparedStatement preparedStatement = db.prepareStatement(sql)){
                preparedStatement.setLong(1, request.getMessageId());
                preparedStatement.executeUpdate();
                responseObserver.onNext(MessagesProto.DeleteMessageResponse.newBuilder().setStatus("OK").build());
                responseObserver.onCompleted();
            } catch (SQLException e) {
                responseObserver.onError(Status.INTERNAL.withDescription("Database error: " + e.getMessage()).asException());
            }
        }

        @Override
        public void editMessage(MessagesProto.EditMessageRequest request, StreamObserver<MessagesProto.EditMessageResponse> responseObserver) {
            String sql = "UPDATE messages SET text = ?, is_edited = true WHERE id = ?;";
            try (PreparedStatement preparedStatement = db.prepareStatement(sql)){
                preparedStatement.setString(1, request.getText());
                preparedStatement.setLong(2, request.getMessageId());
                preparedStatement.executeUpdate();
                responseObserver.onNext(MessagesProto.EditMessageResponse.newBuilder().setStatus("OK").build());
                responseObserver.onCompleted();
            } catch (SQLException e) {
                responseObserver.onError(Status.INTERNAL.withDescription("Database error: " + e.getMessage()).asException());
            }
        }

        @Override
        public void getChatMessages(MessagesProto.GetChatMessagesRequest request, StreamObserver<MessagesProto.GetChatMessagesResponse> responseObserver) {
            String sql = "SELECT m.id, m.user_id, m.is_edited, u.name, m.text, t.project_name FROM messages m JOIN users u ON m.user_id = u.id JOIN chats c ON c.id = m.chat_id JOIN teams t ON c.team_id = t.id WHERE chat_id = ?";
            try (PreparedStatement preparedStatement = db.prepareStatement(sql)) {
                preparedStatement.setLong(1, request.getChatId());
                List<MessagesProto.Message> messages = new ArrayList<MessagesProto.Message>();
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long id = resultSet.getLong("id");
                        long userId = resultSet.getLong("user_id");
                        long chatId = request.getChatId();
                        String text = resultSet.getString("text");
                        boolean isEdited = resultSet.getBoolean("is_edited");
                        String userName = resultSet.getString("name");
                        String projectName = resultSet.getString("project_name");
                        messages.add(MessagesProto.Message.newBuilder().setId(id).setUserId(userId).setChatId(chatId).setUserName(userName).setTeamProjectName(projectName).setIsEdited(isEdited).setText(text).build());
                    }
                    responseObserver.onNext(MessagesProto.GetChatMessagesResponse.newBuilder().addAllMessages(messages).build());
                    responseObserver.onCompleted();
                }
            } catch (SQLException e) {
                responseObserver.onError(Status.INTERNAL.withDescription("Database error: " + e.getMessage()).asException());
            }
        }

        @Override
        public void getUserLastMessages(MessagesProto.GetUserLastMessagesRequest request, StreamObserver<MessagesProto.GetUserLastMessagesResponse> responseObserver) {
            String sql = "SELECT m.text, c.id AS chat_id, u.name, m.user_id, t.project_name FROM messages m JOIN chats c ON m.chat_id = c.id JOIN users u ON m.user_id = u.id JOIN teams t ON c.team_id = t.id JOIN user_in_team ut ON ut.team_id = t.id JOIN ( SELECT chat_id, MAX(id) AS max_id FROM messages GROUP BY chat_id) AS max_messages ON m.chat_id = max_messages.chat_id AND m.id = max_messages.max_id WHERE ut.user_id = ? ORDER BY m.id DESC;";
            try (PreparedStatement preparedStatement = db.prepareStatement(sql)) {
                preparedStatement.setLong(1, request.getUserId());
                List<MessagesProto.Message> messages = new ArrayList<MessagesProto.Message>();
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        long chatId = resultSet.getLong("chat_id");
                        long userId = resultSet.getLong("user_id");
                        String text = resultSet.getString("text");
                        String userName = resultSet.getString("name");
                        String projectName = resultSet.getString("project_name");
                        messages.add(MessagesProto.Message.newBuilder().setUserId(userId).setChatId(chatId).setUserName(userName).setTeamProjectName(projectName).setText(text).build());
                    }
                    responseObserver.onNext(MessagesProto.GetUserLastMessagesResponse.newBuilder().addAllMessage(messages).build());
                    responseObserver.onCompleted();
                }
            } catch (SQLException e) {
                responseObserver.onError(Status.INTERNAL.withDescription("Database error: " + e.getMessage()).asException());
            }
        }

        @Override
        public void postMessage(PostMessageRequest req, StreamObserver<PostMessageResponse> responseObserver) {
            String sql = "INSERT INTO messages (user_id, chat_id, is_edited, text) VALUES (?, ?, ?, ?) RETURNING id;";
            try (PreparedStatement preparedStatement = db.prepareStatement(sql)) {
                preparedStatement.setLong(1, req.getUserId());
                preparedStatement.setLong(2, req.getChatId());
                preparedStatement.setBoolean(3, false);
                preparedStatement.setString(4, req.getText());

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        int generatedId = resultSet.getInt(1);
                        responseObserver.onNext(PostMessageResponse.newBuilder().setId(generatedId).build());
                        System.out.println("Inserted message ID: " + generatedId);
                    }
                }
            } catch (SQLException e) {
                responseObserver.onError(Status.INTERNAL.withDescription("Database error: " + e.getMessage()).asException());
            } finally {
                responseObserver.onCompleted();  // Ensure onCompleted is called outside try/catch
            }
        }
    }
}