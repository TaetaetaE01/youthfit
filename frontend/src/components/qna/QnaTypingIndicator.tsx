export function QnaTypingIndicator() {
  return (
    <div
      role="status"
      aria-label="답변 준비 중"
      className="inline-flex items-center gap-1.5 rounded-2xl rounded-bl-md bg-chat-bubble px-[18px] py-[14px]"
    >
      <span aria-hidden="true" className="block h-[7px] w-[7px] rounded-full bg-chat-soft animate-qna-typing-bounce" />
      <span aria-hidden="true" className="block h-[7px] w-[7px] rounded-full bg-chat-soft animate-qna-typing-bounce [animation-delay:0.15s]" />
      <span aria-hidden="true" className="block h-[7px] w-[7px] rounded-full bg-chat-soft animate-qna-typing-bounce [animation-delay:0.3s]" />
    </div>
  );
}
