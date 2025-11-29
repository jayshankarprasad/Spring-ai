package com.jay.springai.services;

import java.util.List;
import java.util.Map;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.DefaultChatClient.DefaultChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
//import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import com.jay.springai.text.prompttemplate.dto.CountryCuisines;
import com.jay.springai.tools.WeatherTools;

@Service
public class OpenAiService {

	private ChatClient chatClient;

	@Autowired
	private EmbeddingModel embeddingModel;

//	@Autowired
//	private VectorStore vectorStore;

	@Autowired
	private OpenAiImageModel openAiImageModel;
	
	@Autowired
	private OpenAiAudioTranscriptionModel openaiAudioTranscriptionModel;

	@Autowired
	private OpenAiAudioSpeechModel openaiAudioSpeechModel;
	
	@Autowired
	private OpenAiModerationModel moderationModel;

	public OpenAiService(ChatClient.Builder builder, ChatMemory chatMemory) {
		// chatClient=builder.build();
		chatClient = builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
	}

	public ChatResponse generateAnswer(String question) {
		System.out.println("questions::**" + question);
		return chatClient.prompt(question).call().chatResponse();
	}

	public String getTravelGuidance(String city, String month, String language, String budget) {
		PromptTemplate promptTemplate = new PromptTemplate("Welcome to the {city} travel guide!\n"
				+ "If you're visiting in {month}, here's what you can do:\n" + "1. Must-visit attractions.\n"
				+ "2. Local cuisine you must try.\n" + "3. Useful phrases in {language}.\n"
				+ "4. Tips for traveling on a {budget} budget.\n" + "Enjoy your trip!");
		Prompt prompt = promptTemplate
				.create(Map.of("city", city, "month", month, "language", language, "budget", budget));
		return chatClient.prompt(prompt).call().chatResponse().getResult().getOutput().getText();
	}

	public CountryCuisines getCuisines(String country, String numCuisines, String language) {
		PromptTemplate promptTemplate = new PromptTemplate("You are an expert in traditional cuisines.\n"
				+ "You provide information about a specific dish from a specific\n" + "country.\n"
				+ "Answer the question: What is the traditional cuisine of {country}?\n"
				+ "Avoid giving information about fictional places. If the country is\n" + "fictional\n"
				+ "or non-existent answer: I don't know.");
		Prompt prompt = promptTemplate
				.create(Map.of("country", country, "numCuisines", numCuisines, "language", language));
		return chatClient.prompt(prompt).call().entity(CountryCuisines.class);
	}

	public float[] embed(String text) {
		return embeddingModel.embed(text);
	}

	public double findSimilarity(String text1, String text2) {
		List<float[]> response = embeddingModel.embed(List.of(text1, text2));
		return cosineSimilarity(response.get(0), response.get(1));
	}

	private double cosineSimilarity(float[] vectorA, float[] vectorB) {
		if (vectorA.length != vectorB.length) {
			throw new IllegalArgumentException("Vectors must be of the same length");
		}

		// Initialize variables for dot product and magnitudes
		double dotProduct = 0.0;
		double magnitudeA = 0.0;
		double magnitudeB = 0.0;

		// Calculate dot product and magnitudes
		for (int i = 0; i < vectorA.length; i++) {
			dotProduct += vectorA[i] * vectorB[i];
			magnitudeA += vectorA[i] * vectorA[i];
			magnitudeB += vectorB[i] * vectorB[i];
		}

		// Calculate and return cosine similarity
		return dotProduct / (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
	}

//	public List<Document> searchJob(String query){
//		return vectorStore.similaritySearch(query);
//	}

	public String generateImage(String prompt) {
		ImageResponse response = openAiImageModel.call(new ImagePrompt(prompt,
				OpenAiImageOptions.builder().quality("hd").height(1024).width(1024).N(1).build()));
		return response.getResult().getOutput().getUrl();
	}

	public String explainImage(String prompt, String path) {
		String explanation = chatClient.prompt()
				.user(u -> u.text(prompt).media(MimeTypeUtils.IMAGE_JPEG, new FileSystemResource(path))).call()
				.content();
		return explanation;
	}

	public String getDietAdvice(String prompt, String path1, String path2) {

		String explanation = chatClient.prompt()
				.user(u -> u.text(prompt).media(MimeTypeUtils.IMAGE_JPEG, new FileSystemResource(path1))
						.media(MimeTypeUtils.IMAGE_JPEG, new FileSystemResource(path2)))
				.call().content();
		return explanation;
	}
	
	public String speechToText(String path) {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder().language("fr")
				.responseFormat(TranscriptResponseFormat.VTT).build();
		AudioTranscriptionPrompt transcriptionPrompt = new AudioTranscriptionPrompt(new FileSystemResource(path),
				options);
		String output = openaiAudioTranscriptionModel.call(transcriptionPrompt).getResult().getOutput();
		ChatResponse answer = generateAnswer("Generate multiple choice quizzes from text below: " + output);
		return answer.getResult().getOutput().getText();
	}

	public byte[] textToSpeech(String text) {
		return openaiAudioSpeechModel.call(text);

	}
	
	public String callAgent(String query) {
		return chatClient.prompt(query).tools(new WeatherTools()).call().content();
	}
	
	public ModerationResult moderate(String text) {
		Moderation moderation = moderationModel.call(new ModerationPrompt(text)).getResult().getOutput();
		return moderation.getResults().get(0);
	}
}
