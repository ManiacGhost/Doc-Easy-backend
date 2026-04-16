package panscience.chatapp.service;

public interface AiService {
    String summarize(String text);

    String answerQuestion(String context, String question);
}

