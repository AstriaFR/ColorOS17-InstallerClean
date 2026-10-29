package cn.lzy.gt7p.installerclean;

/** Coupled installation flow is atomic; optional features have their own preflight/rollback. */
enum Feature {
    FLOW, PERMISSIONS, RECOMMENDATIONS, RESULT, RESULT_DIALOGS, UPDATE_REMINDERS, CANCEL_PROMPTS, FINISH_NOTE, FINISH_OPEN
}
