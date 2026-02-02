# Crewmeister Test Assignment - Java Backend Developer

## Intro
Thank you for taking the time to complete this challenge as part of your application at Crewmeister!
We are taking development skills very serious and invest a lot of time to find the right candidate. 

At Crewmeister we aim to write excellent software and are convinced that this requires a high level of passion for and 
attention to topics such as software design and principles, best practices and clean code. We take pride in the fact
that the code we produce is extensible, testable, maintainable and runs fast.  

At the same time, we always try to improve the effectiveness of our evaluation and improve the candidate journey
throughout the process. Our aim is that our hiring process is mutually inspiring and feels like a gain for
both parties regardless of the outcome. If you feel to give us feedback on that, please don't hesitate to do so. 

## The Challenge

Your task is to create a foreign exchange rate service as SpringBoot-based microservice. 

The exchange rates can be received from [2]. This is a public service provided by the German central bank.

As we are using user story format to specify our requirements, here are the user stories to implement:

- As a client, I want to get a list of all available currencies
- As a client, I want to get all EUR-FX exchange rates at all available dates as a collection
- As a client, I want to get the EUR-FX exchange rate at particular day
- As a client, I want to get a foreign exchange amount for a given currency converted to EUR on a particular day

If you think that your service would require storage, please use H2 for simplicity, even if this would not be your choice if 
you would implement an endpoint for real clients. 

We are looking out for the following aspects in your submission:
- Well structured and thought-through api and endpoint design 
- Clean code
- Application of best practices & design patterns


That being said it is not enough to "just make it work", show your full potential to write excellent software
 for Crewmeister ! 

## AI Usage Guidelines
 
We encourage you to use AI tools if that reflects your normal development workflow. However, **we expect transparency** in how you use these tools.
 
**Required Documentation:**
  - Create an `AI_USAGE.md` file that includes:
    - Which AI tools you used (e.g., ChatGPT, Claude, GitHub Copilot)
    - Key prompts/questions you asked
    - Relevant AI responses that shaped your solution
    - Your reasoning for accepting, modifying, or rejecting AI suggestions
 
**What We're Looking For:**
  - Understanding of the code you submit (AI-assisted or not)
  - Thoughtful use of AI as a development tool
  - Your own problem-solving and decision-making process
 
Simply asking an AI to "build the entire application" and submitting that output will not demonstrate your skills effectively.
 
## Setup
#### Requirements
- Java 21
- Maven 3.x

#### Project
The project was generated through the Spring initializer [1] for Java
 21 with Spring Boot 3.2.2, dev tools and Spring Web as dependencies. In order to build and
 run it, you just need to click the green arrow in the Application class in your Intellij
 CE IDE or run the following command from your project root und Linux or ios. 

````shell script
$ mvn spring-boot:run
````

After running, the project, switch to your browser and hit http://localhost:8080/api/currencies. You should see some 
demo output. 


[1] https://start.spring.io/

[2] [Bundesbank Daily Exchange Rates](https://www.bundesbank.de/dynamic/action/en/statistics/time-series-databases/time-series-databases/759784/759784?statisticType=BBK_ITS&listId=www_sdks_b01012_3&treeAnchor=WECHSELKURSE)

#### Submission
- Submit completed project via the Greenhouse link in the email received from the Recruitment Manager
- Please send us a link to a github repo that with the solution and make sure that that the branch/repo is not private.
- Please do not submit zipped files



