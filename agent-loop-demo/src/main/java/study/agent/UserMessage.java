package study.agent;

public record UserMessage(String text) implements Message {

    @Override
    public String role() {
        return "user";
    }
}
