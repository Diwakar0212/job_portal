package com.jobportal.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.dto.ProfileDTO;
import com.jobportal.dto.UserDTO;
import com.jobportal.entity.Profile;
import com.jobportal.exception.JobPortalException;
import com.jobportal.repository.ProfileRepository;
import com.jobportal.utility.Utilities;

@Service("profileService")
public class ProfileServiceImpl implements ProfileService {

	@Autowired
	private ProfileRepository profileRepository;

	@Value("${gemini.api.key}")
	private String geminiApiKey;

	private final RestTemplate restTemplate = new RestTemplate();
	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
			.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	@Override
	public Long createProfile(UserDTO userDTO) throws JobPortalException {
		Profile profile = new Profile();
		profile.setId(Utilities.getNextSequenceId("profiles"));
		profile.setEmail(userDTO.getEmail());
		profile.setName(userDTO.getName());
		profile.setSkills(new ArrayList<>());
		profile.setExperiences(new ArrayList<>());
		profile.setCertifications(new ArrayList<>());
		profileRepository.save(profile);
		return profile.getId();
	}

	@Override
	public ProfileDTO getProfile(Long id) throws JobPortalException {
		return profileRepository.findById(id).orElseThrow(() -> new JobPortalException("PROFILE_NOT_FOUND")).toDTO();
	}

	@Override
	public ProfileDTO updateProfile(ProfileDTO profileDTO) throws JobPortalException {
		profileRepository.findById(profileDTO.getId()).orElseThrow(() -> new JobPortalException("PROFILE_NOT_FOUND"));
		profileRepository.save(profileDTO.toEntity());
		return profileDTO;
	}

	@Override
	public List<ProfileDTO> getAllProfiles() throws JobPortalException {
		return profileRepository.findAll().stream().map((x) -> x.toDTO()).toList();
	}

	private String getResolvedGeminiKey() {
		if (geminiApiKey != null && !geminiApiKey.trim().isEmpty() && !geminiApiKey.contains("${")) {
			return geminiApiKey;
		}
		try {
			java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get("../.env"));
			for (String line : lines) {
				if (line.startsWith("GEMINI_API_KEY=")) {
					return line.substring("GEMINI_API_KEY=".length()).replace("\"", "").replace("'", "").trim();
				}
			}
		} catch (Exception e) {}
		return "";
	}

	@Override
	public ProfileDTO parseResume(MultipartFile file) throws Exception {
		// 1. Extract text from PDF
		String text = "";
		try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
			PDFTextStripper stripper = new PDFTextStripper();
			text = stripper.getText(document);
		}

		// 2. Build the prompt
		String prompt = "Extract the candidate's profile information from the following resume text. " +
				"Return ONLY a raw JSON object (no markdown, no ```json blocks) with these exact fields:\n" +
				"{\n" +
				"  \"name\": \"string\",\n" +
				"  \"jobTitle\": \"string (current or most recent job title)\",\n" +
				"  \"company\": \"string (current or most recent company)\",\n" +
				"  \"location\": \"string\",\n" +
				"  \"about\": \"string (a professional summary, 2-3 sentences)\",\n" +
				"  \"totalExp\": number (total years of experience as integer),\n" +
				"  \"skills\": [\"skill1\", \"skill2\", ...],\n" +
				"  \"experiences\": [\n" +
				"    {\n" +
				"      \"title\": \"string\",\n" +
				"      \"company\": \"string\",\n" +
				"      \"location\": \"string\",\n" +
				"      \"startDate\": \"YYYY-MM-DDTHH:mm:ss (e.g. 2020-01-01T00:00:00, use 01 for day/month if unknown, or null if totally missing)\",\n" +
				"      \"endDate\": \"YYYY-MM-DDTHH:mm:ss (use null if currently working or missing)\",\n" +
				"      \"working\": boolean (true if currently working here),\n" +
				"      \"description\": \"string\"\n" +
				"    }\n" +
				"  ],\n" +
				"  \"certifications\": [\n" +
				"    {\n" +
				"      \"name\": \"string\",\n" +
				"      \"issuer\": \"string\",\n" +
				"      \"issueDate\": \"YYYY-MM-DDTHH:mm:ss (e.g. 2020-01-01T00:00:00, or null if missing)\",\n" +
				"      \"certificateId\": \"string\"\n" +
				"    }\n" +
				"  ]\n" +
				"}\n\n" +
				"If a field is not found in the resume, use null for strings, 0 for numbers, and empty arrays for lists.\n\n"
				+
				"Resume Text:\n" + text;

		// 3. Calling Gemini API directly
		String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key="
				+ getResolvedGeminiKey();

		Map<String, Object> requestBody = Map.of(
				"contents", List.of(
						Map.of("parts", List.of(
								Map.of("text", prompt)))));

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

		String responseStr = restTemplate.postForObject(url, entity, String.class);

		// 4. Parsing Gemini response
		JsonNode root = objectMapper.readTree(responseStr);
		String aiText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

		aiText = aiText.trim();
		if (aiText.startsWith("```json")) {
			aiText = aiText.substring(7);
		}
		if (aiText.startsWith("```")) {
			aiText = aiText.substring(3);
		}
		if (aiText.endsWith("```")) {
			aiText = aiText.substring(0, aiText.length() - 3);
		}
		aiText = aiText.trim();

		// 5. Map to ProfileDTO
		return objectMapper.readValue(aiText, ProfileDTO.class);
	}

}
