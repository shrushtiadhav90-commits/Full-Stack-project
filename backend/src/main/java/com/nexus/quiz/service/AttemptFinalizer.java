package com.nexus.quiz.service;

import com.nexus.quiz.entity.QuizAttempt;

public interface AttemptFinalizer {

    void submit(QuizAttempt attempt);
}
