package com.jay.springai.text;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.jay.springai.services.OpenAiService;


@RestController
public class AnswerAnyThingStreamingController {

	@Autowired
	OpenAiService service;

}