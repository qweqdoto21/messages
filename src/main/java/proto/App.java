package proto;

import com.google.protobuf.Message;
import proto.MessagesProto;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import proto.MessagesGrpc;
import proto.MessagesProto.PostMessageResponse;
import proto.MessagesProto.PostMessageRequest;

import java.sql.*;

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