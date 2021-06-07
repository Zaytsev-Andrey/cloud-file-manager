package ru.geekbrains.connection;

/**
 * Observable subject
 */
public interface ConnectionObservable {
    void registerObserver(ConnectionObserver observer);

    void removeObserver(ConnectionObserver observer);

    void notifyAllObserver();

    void notifyObserver(ConnectionObserver observer);
}
