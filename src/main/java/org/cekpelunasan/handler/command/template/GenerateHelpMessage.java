package org.cekpelunasan.handler.command.template;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GenerateHelpMessage {

    private final Map<String, CommandProcessor> commandMap = new HashMap<>();

    public GenerateHelpMessage(List<CommandProcessor> processors) {
        for (CommandProcessor processor : processors) {
            commandMap.put(processor.getCommand(), processor);
        }
    }
    public String generateHelpText() {
        StringBuilder stringBuilder = new StringBuilder("*List Command*:\n");
        for (CommandProcessor cp : commandMap.values()) {
            stringBuilder.append(cp.getCommand()).append(" - ").append(cp.getDescription()).append("\n");
        }
        return stringBuilder.toString();
    }


}
