# TAKATUF | تكاتف

## A Platform for Student Team Formation and Collaboration

**PROJECT CHARTER**  
Senior Project  
Prince Sultan University

**Prepared by**

- Asayil Al Luhayb — 222410382 — SE-AI
- Nisreen Almutairi — 221511326 — SE-CYS
- Fajer Alkhamash — 221410091 — SE-AI
- Afnan Almuallim — 219410692 — SE-CYS
- Shouq Almashouq — 221410803 — SE

**Project Supervisor:** ______________________________  
**Department:** ______________________________  
**Academic Year / Semester:** ______________________________  
**Submission Date:** ______________________________

Academic Weeks 5–14 · Three Iterative Sprints · Five-Member Team

## 1. Project Title and Description

**Project title:**

Takatuf

**Project subtitle:**

Intelligent Team Formation and Project Collaboration

Takatuf is an Android application that helps university students discover project opportunities, find suitable teammates, and coordinate their work. It supports student-led projects as well as teams formed for hackathons, competitions, and innovation challenges.

Students can publish a project or create a team request linked to an event, describe the roles they need, and receive applications from interested students. Takatuf uses applicants’ skills, experience, interests, and availability to recommend complementary team combinations. Recommendations consider the team’s existing members and explain both the capabilities covered and the gaps that remain.

The project owner makes the final selection, and applicants confirm their participation before joining. Once a team is formed, a shared workspace supports responsibilities, milestones, progress updates, and contribution records. These records help students present their experience when applying to future teams.

The project will deliver an integrated Android prototype, supported by a backend service and database, within academic weeks 5–14.

## 2. Project Objectives

Takatuf addresses the difficulty of finding suitable collaborators when recruitment depends on personal contacts and information scattered across different channels. It also addresses missed competition opportunities, uncertainty about applicants’ capabilities, and disruption when a team member leaves.

The project’s primary objectives are to:

1. Improve access to opportunities by bringing student projects, competitions, hackathons, and related team requests into one searchable platform.

2. Support informed team selection by presenting relevant applicant information and explaining how different member combinations meet a team’s needs.

3. Make recruitment easier to manage through clear application states, joining offers, participation confirmation, and replacement recruitment.

4. Support continuity during collaboration by keeping responsibilities, milestones, updates, and deliverable links accessible to the team.

5. Build useful participation records through recorded contributions, completion evidence, and feedback from people who worked together.

6. Evaluate the proposed solution through functional testing, recommendation comparisons, and feedback from representative student users.

## 3. Project Scope

### 3.1 In Scope

**Student profiles and discovery**

Students will maintain Skill Passports containing their skills, interests, experience, availability, and optional work evidence. Search, filters, and saved listings will help them find relevant opportunities.

**Projects, competitions, and hackathons**

Students will be able to publish new project ideas, recruit for existing projects, and share competition or hackathon listings. Event listings will identify their source and organizer and include relevant dates, eligibility conditions, and team-size rules. Team requests can be linked to an event so applicants understand the opportunity they are joining.

**Recruitment and intelligent team formation**

The platform will support applications, shortlisting, team recommendations, comparison of alternatives, gap explanations, offers, and joining confirmation. Owners can retain selected members while exploring other combinations.

**Collaboration and continued recruitment**

Confirmed teams will have a basic workspace for responsibilities, milestones, updates, and deliverable links. Teams can reopen vacancies, manage departures, and transfer leadership while retaining their project records.

**Participation records and platform administration**

The application will support contribution records, completion evidence, participant feedback, notifications, profile visibility settings, and basic moderation.

### 3.2 Out of Scope

The following capabilities are outside the planned delivery:

- A separate full-featured web application or iOS application.

- Payments, subscriptions, prize distribution, or financial transactions.

- Official competition registration, judging, or event administration.

- Automated scraping of external competition and hackathon websites.

- AI-generated project ideas.

- Advanced skill examinations or formal certification of student proficiency.

- A comprehensive project-management suite with features such as resource forecasting and complex scheduling.

- Large-scale commercial deployment and operation.

## 4. Stakeholders and Responsibilities

### 4.1 Student Users

University students are the primary users. They create profiles, discover opportunities, apply to teams, and contribute to projects. They expect clear information, manageable recruitment, appropriate privacy controls, and a fair representation of their participation.

### 4.2 Project and Team Owners

Owners define the purpose of a team, specify required roles, assess applicants, and coordinate confirmed members. They expect useful recommendations, visibility into missing capabilities, and control over membership decisions. Ownership is specific to each project; a student may lead one team and participate in another.

### 4.3 Competition and Hackathon Organizers

Organizers provide the official event information referenced by listings and linked team requests. Their expectations include accurate attribution, clear registration links, and faithful presentation of event rules. A student who shares an event listing remains identifiable as its publisher, separately from the organizer.

### 4.4 Platform Administrator

The administrator manages reported content, inappropriate activity, and feedback disputes through a basic administration facility. This role requires controlled access to relevant records and a traceable record of moderation decisions.

### 4.5 Project Supervisor and Academic Evaluators

The supervisor provides academic guidance and reviews project progress. Evaluators assess whether the implementation, analysis, and supporting evidence meet the senior project requirements. They expect a coherent scope, clear team contributions, and credible validation of the delivered system.

### 4.6 Development Team

Each team member leads an area while participating in integration, peer review, and testing.

- Asayil Al Luhayb — 222410382, SE-AI: Android interface, onboarding, profiles, and opportunity discovery.

- Nisreen Almutairi — 221511326, SE-CYS: Backend services, database, listings, applications, and membership permissions.

- Fajer Alkhamash — 221410091, SE-AI: Applicant matching, team recommendations, gap explanations, and recommendation evaluation.

- Afnan Almuallim — 219410692, SE-CYS: Team workspace, membership changes, notifications, and feedback.

- Shouq Almashouq — 221410803, SE: Test datasets, system testing, moderation functions, and deployment.

The named lead coordinates the work in an area; implementation and verification remain shared team responsibilities.

## 5. Delivery Approach and Milestones

The team will use an iterative Agile approach with three sprints. Each sprint will produce integrated functionality that can be tested through the Android application and its backend.

Development tasks, defects, and decisions will be maintained in a shared backlog. Student survey findings and targeted feedback will guide requirement refinement and interface decisions. Changes affecting the agreed scope, schedule, or budget will be reviewed with the supervisor.

### Sprint 1 — Weeks 5–8

Application foundation and recruitment

- Establish the Android application, backend services, and database.

- Implement accounts, Skill Passports, and profile visibility.

- Build project, competition, and hackathon listings.

- Add event-linked team requests, search, applications, and applicant management.

- Develop an early matching experiment to test the suitability of the chosen profile and team data.

Milestone:  A student can discover a project or event-linked team request, apply, and have the application reviewed by its owner.

### Sprint 2 — Weeks 9–11

Intelligent team formation and collaboration

- Integrate applicant matching and complementary team recommendations.

- Add team comparison, retained-member selection, and gap explanations.

- Implement joining offers and membership confirmation.

- Build the team workspace and milestones.

- Support vacancy reopening and leadership changes.

Milestone:  An owner can compare applicants, form a confirmed team, and manage its initial collaboration.

### Sprint 3 — Weeks 12–14

Participation records and system reliability

- Complete contribution records, feedback, notifications, and moderation.

- Implement local draft recovery and safe handling of interrupted requests.

- Test permissions, membership transitions, and recommendation behaviour.

- Refine usability and deploy the integrated application and backend.

Milestone:  The complete user journey operates reliably from opportunity discovery through collaboration and recorded participation.

## 6. Project Constraints

### 6.1 Timeframe

Development is limited to academic weeks 5–14. The team must sequence work so that account, listing, and application functions are stable before recommendation and collaboration features depend on them.

### 6.2 Budget

The planned cash budget is SAR 350, comprising:

- LLM API experiments: SAR 150.

- Prototype hosting: SAR 135.

- Contingency: SAR 65.

These are spending allowances. Actual service choices will be checked against current prices before purchase, and usage will be monitored throughout development.

### 6.3 Team and Equipment

Five students will carry out the project alongside their academic commitments. Development and testing will use existing computers, available Android devices, and accessible development tools. Shared code ownership and peer review will reduce dependence on any single member.

### 6.4 Technical Environment

The planned implementation uses an Android client, a Python backend, and a relational database. Hosting capacity and available test devices will influence the scale of evaluation. Frameworks and supporting libraries will be selected according to integration effort, maintainability, and team familiarity.

Team matching will use a rule-based approach with weighted scoring of applicants’ skills, experience, interests, and availability, followed by a greedy selection algorithm that progressively adds applicants who best complement the team’s remaining needs. The process will account for existing members, recorded eligibility conditions, and available places, and explain the resulting strengths and gaps.

An optional LLM service may assist with interpreting project descriptions. Students will confirm suggested role or skill information before it is used for matching. Structured project and profile data will support recruitment when the service is unavailable.

### 6.5 Data and Evaluation

Evaluation will use synthetic student profiles and controlled recruitment scenarios to test skill combinations, availability conflicts, team sizes, and missing-role cases. Development scenarios will be kept separate from the cases used for final evaluation.

Where feasible, a small group of consenting PSU students will also participate using their actual skills, interests, and availability. They will indicate interest in selected project or competition scenarios, creating a separate applicant pool for each. Their feedback will help assess whether suggested teams are suitable and the explanations are clear. Identifying information will be removed from reported examples.

Results from synthetic scenarios and the student pilot will be reported separately, with conclusions reflecting the sample size and situations tested.

### 6.6 External Event Information

Competition information depends on its published source and may change during recruitment. Listings therefore need source links, identifiable publishers, and a way to update or report inaccurate information.

## 7. Risk Register

Likelihood and impact use a three-point scale: 1 = Low, 2 = Medium, 3 = High. The risk score is likelihood × impact: 1–2 = Low, 3–4 = Medium, 6–9 = High.

The team will review risks at each sprint review and when a significant issue occurs. Owners coordinate mitigation and report changes in risk status.

| ID | Risk | Likelihood | Impact | Score | Mitigation and contingency | Owner |
| --- | --- | --- | --- | --- | --- | --- |
| R1 | Feature development or integration exceeds the available schedule. | 3 | 3 | 9 — High | Integrate features throughout each sprint and track dependencies. If delays occur, simplify implementation details and prioritize the essential user journeys. | Asayil |
| R2 | Limited or unrealistic profile data produces misleading recommendation results. | 3 | 3 | 9 — High | Use varied recruitment scenarios, separate development and evaluation cases, and include a small PSU student pilot where feasible. Report synthetic and real-student results separately and state the limits of the evidence. | Fajer |
| R3 | Team search becomes slow or produces unsuitable combinations. | 2 | 3 | 6 — High | Test the recommendation approach early with different applicant pools and constraints. Adjust the search method and return explicit gaps when a complete team cannot be formed. | Fajer |
| R4 | Permission or membership defects expose records or create inconsistent team states. | 2 | 3 | 6 — High | Enforce authorization on the backend and test role changes, simultaneous offers, and repeated requests. Restrict affected functions while defects are corrected. | Nisreen |
| R5 | External API outages or unexpected usage costs interrupt development. | 2 | 2 | 4 — Medium | Monitor spending, handle service failures, and keep external integrations separate from essential recruitment functions. Continue with structured inputs when necessary. | Nisreen |
| R6 | Event information becomes outdated or is incorrectly attributed. | 2 | 2 | 4 — Medium | Store source links and update dates, distinguish publishers from organizers, and support reporting. Flag affected listings until their details are corrected. | Afnan |
| R7 | Feedback is used unfairly or contribution claims are disputed. | 2 | 2 | 4 — Medium | Restrict reviews to recorded participants, distinguish evidence from claims, and provide reporting and administrator review. Preserve relevant records during a dispute. | Afnan |
| R8 | Too few representative students participate in evaluation. | 2 | 2 | 4 — Medium | Recruit through relevant student groups and include different team-search experiences. Use the available sample transparently and supplement it with structured scenario testing. | Shouq |
| R9 | Member absence, equipment failure, or lost work disrupts delivery. | 2 | 3 | 6 — High | Use a shared repository, regular backups, and peer review. Reassign tasks and recover work from verified copies when needed. | Shouq |

## 8. Key Performance Indicators

The following indicators are proposed evaluation targets. They will be measured using recorded test results, controlled scenarios, and student feedback.

**Delivery completion**

All user stories marked Must should pass their acceptance criteria by the end of week 14. Any remaining Should stories will be recorded with their implementation status.

**Recruitment integrity**

All defined tests for joining confirmation, membership limits, duplicate actions, and project permissions should pass. No known critical authorization or membership defect should remain in the final build.

**Recommendation validity**

Every recommendation in the evaluation set should use eligible active applicants from the relevant team request and preserve the selected existing members. Cases without a feasible complete team should produce a clear explanation.

**Recommendation usefulness**

Measure skill coverage and unmet essential roles against random eligible selection, individual-fit ranking, and a simple gap-filling method. The target is to match or improve on individual-fit ranking for average requirement coverage across the evaluation cases.

**Student usability**

Recruit a target of at least ten PSU student participants. Aim for at least 80% successful completion of the assigned core tasks without facilitator intervention, while recording recurring points of confusion.

**Operational performance**

Record response times, failed requests, and recovery behaviour on the agreed test devices and workloads. Essential workflows should complete without crashes or unresolved failures. Workload definitions and acceptable waiting times will be established during Sprint 1 testing.

**Budget control**

Keep total project spending within SAR 350 and record expenditure against the three budget allowances.

## 9. System Requirements

The following requirements define the system’s planned functions and quality expectations. They will be validated through student input and maintained in the development backlog.

### 9.1 Functional Requirements

- **FR01 – Account access:** Support registration, sign-in, sign-out, and account recovery.
- **FR02 – Skill Passports:** Let students maintain skills, proficiency, interests, experience, availability, and optional work evidence.
- **FR03 – Profile visibility:** Let students control who can view their profile information.
- **FR04 – Project listings:** Allow owners to publish and update project outcomes, required roles, commitment, and vacancies.
- **FR05 – Competition and hackathon listings:** Support event listings with organizers, official sources, dates, eligibility, and team-size rules.
- **FR06 – Event-linked team requests:** Allow team requests linked to competitions or hackathons, with relevant event details visible to applicants.
- **FR07 – Opportunity discovery:** Support searching, filtering, and saving projects, events, and team requests.
- **FR08 – Role templates:** Provide editable templates for describing recruitment needs.
- **FR09 – Applications:** Let students apply to open requests, state their contribution and availability, and withdraw applications.
- **FR10 – Applicant management:** Let owners review applications, shortlist candidates, and track application status.
- **FR11 – Applicant matching:** Assess active applicants against the request using profile information, existing members, and eligibility conditions.
- **FR12 – Team recommendations:** Suggest complementary teams and explain covered capabilities, gaps, and incomplete-team outcomes.
- **FR13 – Team comparison:** Let owners compare suggestions, retain selected people, and regenerate alternatives.
- **FR14 – Membership confirmation:** Support offers, acceptance, rejection, and expiry, requiring applicant confirmation within team limits.
- **FR15 – Team workspace:** Provide confirmed members with shared responsibilities, progress updates, and deliverable links.
- **FR16 – Milestones:** Support milestone creation, assignment, progress tracking, and outstanding-work visibility.
- **FR17 – Continued recruitment:** Reopen vacancies and refresh recommendations while preserving progress and reconfirming previous applicants’ interest.
- **FR18 – Departures and leadership:** Support member departure and leadership transfer with successor agreement and updated permissions.
- **FR19 – Notifications:** Notify relevant users about recruitment decisions, offers, membership, milestones, and significant event or request changes.
- **FR20 – Participation records:** Record participation, contributions, completion, and supporting evidence in Skill Passports.
- **FR21 – Participant feedback:** Allow recorded participants to review contributions and leadership, flag disputed feedback, and distinguish missing reviews from poor performance.
- **FR22 – Reporting and moderation:** Support reports on listings, conduct, and feedback, with administrator review and recorded actions.

### 9.2 Non-Functional Requirements

- **NFR01 – Security:** Encrypt application–backend communication and protect stored credentials and service secrets.
- **NFR02 – Authorization:** Enforce backend permissions based on identity, membership, and role, including requests made outside the interface.
- **NFR03 – Privacy:** Collect relevant information, enforce visibility settings, and protect sensitive data in logs and errors.
- **NFR04 – Data integrity:** Keep application, offer, and membership updates consistent, preventing duplicates and capacity violations during repeated or concurrent requests.
- **NFR05 – Failure handling:** Provide clear errors and recovery options, keeping essential recruitment usable during optional-service failures.
- **NFR06 – Connection resilience:** Preserve drafts in supported forms, allow safe retries, and distinguish saved, pending, and failed actions.
- **NFR07 – Usability:** Use consistent navigation and labels, clear action confirmations, and guidance for missing information.
- **NFR08 – Accessibility:** Use readable text, sufficient contrast, labelled controls, and cues beyond colour; check main tasks with enlarged text and Android accessibility tools.
- **NFR09 – Performance:** Keep core workflows usable under the agreed test workload, show waiting states, and limit recommendation search time.
- **NFR10 – Transparency:** Explain recommendation factors, gaps, and scores in terms students can understand.
- **NFR11 – Consistent assessment:** Apply relevant declared criteria and distinguish missing evidence or history from poor performance.
- **NFR12 – Maintainability:** Separate component responsibilities and use shared coding conventions and peer review.
- **NFR13 – Compatibility:** Test and record support for the selected Android versions, devices, and screen sizes.
- **NFR14 – Traceability and recovery:** Log key membership, recommendation, and moderation actions, maintain backups, and verify restoration before delivery.

## 10. User Stories and Acceptance Criteria

The user stories translate the requirements into work that can be verified during each sprint.

Must indicates functionality needed for the essential user journey or reliable operation. Should indicates valuable functionality that follows the essential work if delivery pressure arises.

### 10.1 Sprint 1 — Weeks 5–8

| ID and Title | User Story | Acceptance Criteria | Priority |
| --- | --- | --- | --- |
| US01<br>Account access | As a student, I want to create and access an account so that I can manage my participation. | Valid registration creates an account; invalid or duplicate details produce clear feedback; sign-in, sign-out, and account recovery work; protected actions require authentication. | Must |
| US02<br>Skill Passport and privacy | As a student, I want to describe my capabilities and control who sees my information so that I can present myself appropriately to potential teams. | Skills, interests, experience, and availability can be saved and edited; optional evidence links can be added; selected visibility settings are enforced when another user views the profile. | Must |
| US03<br>Project recruitment listing | As a project owner, I want to publish the roles and commitment my project needs so that suitable students can apply. | A listing captures the project outcome, roles, commitment, and open positions; the owner can edit it; editable role templates are available; required fields are validated. | Must |
| US04<br>Competition and hackathon listing | As a student, I want to share a competition or hackathon so that other students can discover it and check its details. | A listing records the event’s organizer, source, dates, eligibility, and team-size rules; the publisher is distinguishable from the organizer; expired listings are visibly identified. | Must |
| US05<br>Team request linked to an event | As a student entering an event, I want to create a linked team request so that applicants understand the challenge and recruitment needs. | The request links to an event; needed roles and available places are recorded; applicants can view relevant event rules and the official source; the owner can update recruitment details. | Must |
| US06<br>Discover and save opportunities | As a student, I want to search and save relevant opportunities so that I can decide where to participate. | Search and filters return matching listings; students can open the associated details; saved listings can be retrieved and removed; an empty search provides a clear message. | Must |
| US07<br>Apply and withdraw | As a student, I want to apply to a team and withdraw when necessary so that my interest remains accurate. | An application records intended contribution and availability; duplicate active applications to the same request are prevented; withdrawal updates its status; closed requests do not accept new applications. | Must |
| US08<br>Review applicants | As a team owner, I want to review and shortlist applicants so that I can organize recruitment decisions. | The owner can view authorized applicant details and application states; candidates can be shortlisted or removed from the shortlist; shortlisting leaves membership unchanged; unrelated users cannot manage applications. | Must |

### 10.2 Sprint 2 — Weeks 9–11

| ID and Title | User Story | Acceptance Criteria | Priority |
| --- | --- | --- | --- |
| US09<br>Applicant fit | As a team owner, I want to understand how applicants fit the requested roles so that I can make informed selections. | Assessment uses active applicants to the relevant request; recorded skills, availability, and eligibility are considered; results identify the main fit factors; existing members are included in the team context. | Must |
| US10<br>Complementary team recommendations | As a team owner, I want suggested member combinations so that I can assess the capabilities of the whole team. | Suggestions account for existing members and available places; covered and missing capabilities are displayed; a case without a suitable complete team produces an explanation; suggestions do not change membership automatically. | Must |
| US11<br>Compare and retain candidates | As a team owner, I want to compare alternatives while retaining selected people so that I can explore practical team choices. | At least two alternatives can be compared when available; retained candidates remain in regenerated alternatives; differences in coverage and gaps are shown; incompatible retained selections produce a clear explanation. | Should |
| US12<br>Confirm joining | As an applicant, I want to accept or decline a joining offer so that I control whether I become a member. | An owner can send an offer; only the intended applicant can accept or decline it; acceptance creates confirmed membership; expired offers cannot be accepted; repeated actions and simultaneous acceptances respect team capacity. | Must |
| US13<br>Shared team workspace | As a confirmed member, I want a shared place for responsibilities and updates so that I can coordinate with my team. | Confirmed members can access the workspace; responsibilities, updates, and deliverable links can be recorded; changes follow project permissions; unrelated users cannot access private workspace content. | Must |
| US14<br>Track milestones | As a team member, I want to see assigned milestones and update progress so that outstanding work is clear. | Milestones include a title, responsible member, target date, and status; authorized members can update them; outstanding and completed milestones remain distinguishable. | Must |
| US15<br>Reopen a vacancy | As a team owner, I want to recruit a replacement without losing project progress so that the team can continue working. | A vacancy can be reopened in the existing project; workspace records remain available; recommendations reflect current members and revised needs; previous applicants reconfirm interest before being reconsidered. | Must |
| US16<br>Manage departure and leadership | As a team member, I want a clear process for leaving or transferring leadership so that responsibilities and access remain accurate. | A departure updates membership and access; the leader can nominate a successor; leadership changes only after the successor accepts; pending or declined transfers preserve the current owner. | Must |

### 10.3 Sprint 3 — Weeks 12–14

| ID and Title | User Story | Acceptance Criteria | Priority |
| --- | --- | --- | --- |
| US17<br>Record participation and outcomes | As a student, I want my project contributions recorded so that I can present relevant experience to future teams. | Authorized users can record contributions and supporting links; participation and project completion states are distinguishable; Skill Passports display recorded contributions; evidence-backed and unsupported claims are distinguishable. | Must |
| US18<br>Give and report feedback | As a participant, I want to review my team experience and report disputed feedback so that participation records remain useful and fair. | Reviews are available only to recorded participants after completion or departure; contribution and leadership feedback are supported; feedback can be reported; disputed records are identifiable; a lack of reviews is not displayed as poor performance. | Should |
| US19<br>Receive relevant notifications | As a student, I want updates about actions affecting me so that I can respond at the right time. | Relevant users receive notifications for decisions, offers, membership changes, milestones, and recorded material event changes; notifications open the related item; read and unread states are distinguishable; retries do not create duplicate notifications for the same action. | Must |
| US20<br>Report and moderate issues | As an administrator, I want to review user reports so that misleading content and inappropriate activity can be addressed. | Users can report relevant content or activity; an administrator can view and update report status; moderation actions include a recorded reason; access to administrative actions is restricted. | Must |
| US21<br>Recover interrupted work | As a student with an unstable connection, I want my drafts and pending actions handled clearly so that I can continue without re-entering work. | Supported forms restore saved local drafts; failed requests show a retry action; pending and completed states are distinguishable; retrying a submitted action does not create a duplicate record. | Must |
| US22<br>Keep access accurate | As a student, I want private records accessible only to authorized people so that my information remains protected as team membership changes. | Tests cover owners, applicants, members, former members, and administrators; backend requests enforce the same permissions as the interface; departure and leadership changes update access; relevant actions are recorded for investigation. | Must |
| US23<br>Complete tasks on Android | As a student, I want the main tasks to work clearly on my Android device so that I can use the platform without unnecessary assistance. | Core journeys are checked on the selected devices and screen sizes; enlarged text and labelled controls support main tasks; loading, empty, and error states are understandable; blocking usability defects are corrected. | Must |
| US24<br>Use the integrated service reliably | As a student, I want my actions and project records to remain consistent so that I can rely on the platform throughout recruitment and collaboration. | The deployed application completes the integrated discovery-to-participation journey; defined recommendation and failure scenarios pass; optional service failures provide a usable recovery path; backup restoration is verified; no known critical defect remains. | Must |

## 11. Completion and Charter Approval

The project will be considered complete when the integrated application meets the agreed Must-story acceptance criteria, the evaluation results are recorded against the KPIs, and significant unresolved issues are disclosed for academic assessment.

The development team and supervisor will use this charter as the reference for scope and delivery decisions. Material changes will be assessed for their effect on functionality, time, cost, and validation before they are accepted.

Project supervisor:  __________________________________________________

Supervisor approval:  __________________________________________________

Team representative:  __________________________________________________

Approval date:  __________________________________________________
