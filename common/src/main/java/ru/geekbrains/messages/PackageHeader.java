package ru.geekbrains.messages;

import ru.geekbrains.commands.NetworkCommand;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains command string and array params.
 * Implements pattern builder.
 */
public class PackageHeader implements Serializable {
    private NetworkCommand command;
    private List<String> params;

    public static class HeaderBuilder {
        private NetworkCommand command;
        private List<String> params;

        public HeaderBuilder(NetworkCommand command) {
            this.command = command;
            params = new ArrayList<>();
        }

        public HeaderBuilder addParam(String param) {
            params.add(param);
            return this;
        }

        public PackageHeader build() {
            return new PackageHeader(this);
        }

    }

    private PackageHeader(HeaderBuilder builder) {
        this.command = builder.command;
        this.params = builder.params;
    }

    public NetworkCommand getCommand() {
        return command;
    }

    public String getParam(int index) {
        return params.get(index - 1);
    }
}
