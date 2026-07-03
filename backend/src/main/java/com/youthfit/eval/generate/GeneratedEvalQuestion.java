package com.youthfit.eval.generate;

import com.youthfit.eval.dataset.EvalQuestionType;

public record GeneratedEvalQuestion(String question, EvalQuestionType questionType, String snippet) {}
