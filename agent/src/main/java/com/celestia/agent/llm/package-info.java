/**
 * The answering middle of the reply path (SPEC-012): the chat model, the tools it
 * may call, and the prompt that constrains it.
 *
 * <h2>Three rules that are not obvious from the code</h2>
 *
 * <ol>
 *   <li><b>One route to the model, named at two classes.</b>
 *       {@link com.celestia.agent.llm.OwnerAnswerer} is the only type that
 *       invokes a chat model, and {@code GuardedPipeline} is the only type that
 *       calls it. Two ArchUnit rules hold this: one forbids any other class from
 *       <em>calling</em> {@code ChatModel.call}, {@code ChatModel.stream} or
 *       {@code ChatClient.prompt}; the other forbids any class outside this
 *       package from <em>naming</em> a Spring AI type at all. Adding a second
 *       call site, or moving one of these classes out of this package, fails the
 *       build — which is the point (Constitution V, NON-NEGOTIABLE).
 *   <li><b>The model never sees a toolkit string.</b> Every tool result's
 *       {@code {sheetId, sheetTitle, row}} is swapped for a per-turn reference —
 *       {@code s1}, {@code s2} — by {@link com.celestia.agent.llm.SourceReferences}
 *       before serialisation, and the real sources go to the turn record. The
 *       reference is an <em>index, never an encoding</em>: a hash or a slug would
 *       put the sheet id back in the model's context by another route. Changing a
 *       tool adapter to return a sheet title would defeat Constitution X in one
 *       call (FR-013a–d).
 *   <li><b>The prompt is versioned with the code.</b>
 *       {@code system-prompt.md} is a classpath resource, not a property and not a
 *       database row, because it is the only thing holding several NON-NEGOTIABLE
 *       obligations. A prompt editable in production is a guardrail editable in
 *       production. {@code SystemPromptTest} asserts every obligation is present,
 *       so removing one is a build failure rather than a discovery.
 * </ol>
 *
 * <h2>Why identity is not a tool argument</h2>
 *
 * <p>No {@code @Tool} method accepts a {@code userId}, a coordinate or a clock.
 * They come from {@link com.celestia.agent.llm.TurnScope}, bound to the answering
 * thread for the life of one turn. The enforcement is that there is <b>nowhere to
 * put them</b>: a model that tries to pass someone else's identity has no
 * parameter for it (FR-006, Constitution V).
 *
 * <p>The same scope is what makes a turn <em>bounded</em> and <em>reviewable</em>.
 * Spring AI runs the tool loop inside the chat model and calls a
 * {@code ToolCallingManager} once per round, so
 * {@link com.celestia.agent.llm.TurnToolCallingManager} is the one place a round
 * can be counted, a deadline checked, and a tool call recorded. That is also why
 * this package uses {@code ChatModel} directly rather than the fluent
 * {@code ChatClient}, whose loop is private and cannot be bounded.
 *
 * <h2>Statelessness</h2>
 *
 * <p>There is no {@code ChatMemory} bean and no carried subject. Each question is
 * answered on its own, and the model is shown no previous message text (FR-018).
 * A question that names its own matter still works, because the owner's chart is
 * already context — not because anything was remembered.
 */
package com.celestia.agent.llm;
