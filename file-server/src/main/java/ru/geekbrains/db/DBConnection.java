package ru.geekbrains.db;

import ru.geekbrains.entities.FileServerClient;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Interface for interaction of the server application with the DB
 */
public interface DBConnection extends AutoCloseable {
    Optional<FileServerClient> login(String login, String password) throws SQLException;
}
