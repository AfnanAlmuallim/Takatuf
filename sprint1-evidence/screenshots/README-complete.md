# Takatuf Sprint 1 application demonstration screenshots

80 numbered screenshots captured from the actual running Android application on 5 October 2026.
Device: Samsung SM-G960W, Android 10 (API 29), 1080 × 2220 screenshots.
Application: com.z.takatuf, debug version 0.1.0 (version code 1), run through Android Studio Preview.

## Scope and requirements
The current deliverable covers application demonstration screenshots. Python backend execution, Firebase administration/testing and backend API documentation are deferred at the user's request. The application’s normal UI was used for authentication and saving demonstration data. These screenshots do not certify backend enforcement or replace executed acceptance tests.

Requirements evidence: Takatuf Sprint 1 Design (1).docx and Takatuf_Project Charter (1).docx, section 12.1 (US01–US08 acceptance criteria), covering FR01–FR10. A Best match option appears in the implemented discovery UI; matching functionality is outside the Sprint 1 baseline and is not counted as a Sprint 1 completion.

## Dedicated demonstration accounts
- Owner: Afnan Almuallim — 219410692.sprint1.20261005@psu.edu.sa.
- Student: Fajer Alkhamash — 221410091.sprint1.20261005@psu.edu.sa.
Names and student IDs were taken from the supplied charter. These are dedicated test aliases, not proof of mailbox access. Passwords are excluded.

## Demonstrated flows and observed states
- Login, account creation, empty-field validation, incorrect-password failure and successful sign-in retry, sign-out confirmation, and password-recovery request form.
- Three-step Skill Passport onboarding, saved profile, basic information, skills, interests, availability, experience and evidence-link editors. Skill level and interest changes were saved.
- Teams you apply to visibility: Afnan's Passport was restricted when viewed by an unrelated student. Fajer's Passport was available to the owner reviewing Fajer's application. Other visibility choices were displayed; Only you enforcement was not exercised.
- Empty and populated discovery, text search with results and no results, listing-type and skill filters, and empty/saved/removed opportunity states.
- Project publishing validation, role template selection, customized vacancy count, commitment settings, published details, owner actions and editing.
- Competition publishing, event details and editing, then changing that same event's type to Hackathon. This does not evidence two separately published events.
- An event-linked recruitment request was created from the event page, published with a UI/UX designer role template, viewed, and opened in its owner editor.
- Student application form, invalid blank pitch, completed pitch, successful submission and Under review status. The application UI ceased offering a second application after submission; direct duplicate requests were not tested.
- Owner empty and populated applicant views, application details and applicant Passport access, shortlist addition, removal and subsequent addition, and student Shortlisted status.
- The project retained its displayed team/vacancy counts after shortlisting. Database-level team membership was not inspected, so no membership-invariant pass is claimed.
- Owner recruitment closure and student Closed details with application disabled.
- Student withdrawal confirmation and Withdrawn application history, including withdrawal after the owner closed the project.
- Event dates were changed from 20–21 October to 1–2 October. The parent event then showed Event ended and no recruitment action.

## Observed issue: expired event-linked request
Figures 78–80 document an inconsistency after editing the parent event dates into the past. The linked request showed the updated 1–2 October dates but retained an enabled Apply button, and its application form opened. No application was sent in this expiry demonstration. Backend acceptance or rejection therefore remains unverified. Figure 79 shows the same parent event labelled Event ended.

## Missing or unverified evidence
- A completed new-password/reset flow was not demonstrated: it needs access to the recipient mailbox. The recovery request form is captured.
- Experience/evidence-link editor screens are captured, but successful addition and invalid-link rejection were not exercised.
- Every owner editing screen shown does not by itself establish that every field was changed and saved.
- Only you privacy enforcement, unauthorized direct data access, direct duplicate applications, vacancy boundaries, deadline boundaries and concurrent updates remain acceptance-test work.
- Event-linked Apply availability after parent expiry is the observed UI issue above; submission enforcement is unverified.
- Python backend, API documentation, Firebase rules/database verification and the early matching feasibility experiment are deferred.
- This is a screenshot evidence package, not the completed Word implementation/testing report or retrospective.

## Files
The ZIP contains 80 original PNG screenshots, figure-register.json with numbered captions and requirement mappings, and this README. The complete HTML gallery references gallery-assets-1.js through gallery-assets-8.js in the screenshots folder. Preserve that folder beside the HTML file when moving the gallery.
