package ru.geekbrains.db;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.geekbrains.configs.DBConfig;
import ru.geekbrains.entities.FileServerClient;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.Optional;

public class MySQLConnection implements DBConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLConnection.class);

    private Connection connection;

    // Hash password settings
    private byte[] saltBytes = "AlojmqKr0j4l0CwR2D7Kf6dITc7GlABX".getBytes();
    private int iterations = 10000;
    private int keyLength = 512;

    public MySQLConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(DBConfig.URL, DBConfig.user, DBConfig.password);
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

    /**
     * Verify login and password
     * @param login - client login
     * @param password - client password
     * @return Optional<Client> contains login, nickname and homeDirectory
     *      or empty Optional<Client> if client no pass verify
     */
    @Override
    public Optional<FileServerClient> login(String login, String password) throws SQLException {
        String secretPassword = hashPassword(password);

        PreparedStatement prStatement = connection.prepareStatement("SELECT nickname, home_directory " +
                "FROM nfs_client " +
                "WHERE login = ? AND password = ?;");
        prStatement.setString(1, login);
        prStatement.setString(2, secretPassword);

        ResultSet result = prStatement.executeQuery();
        Optional<FileServerClient> client = Optional.empty();

        if (result.next()) {
            String nickname = result.getString("nickname");
            String homeDirectory = result.getString("home_directory");
            client = Optional.of(new FileServerClient(login, nickname, homeDirectory));
        }

        return client;
    }

    /**
     * Hashes the password using a PBKDF2 algorithm
     * @param password - client password string
     * @return hashed client password string
     */
    private String hashPassword(String password) {
        char[] passwordChars = password.toCharArray();
        byte[] result = new byte[0];
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            PBEKeySpec spec = new PBEKeySpec(passwordChars, saltBytes, iterations, keyLength);
            SecretKey key = skf.generateSecret(spec);
            result = key.getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            LOGGER.warn("Hashing password error", e);
        }
        return Hex.encodeHexString(result);
    }
}
