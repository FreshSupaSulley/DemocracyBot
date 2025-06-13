# DemocracyBot
A Discord bot that runs a server and distributes power to an elected President every month. Made for the people of the nation of **Discordias**.

Built in Java using [JDA](https://github.com/DV8FromTheWorld/JDA) as a Maven project.

![Our nation's glorious flag](https://github.com/FreshSupaSulley/DemocracyBot/assets/45902499/3278dc05-3fbc-414f-8717-b233af1c312b)

# CI/CD Idea
On commit:
1. Build the runnable jar with GitHub Actions
2. Update the release file
3. Send a webhook to Discord
4. DemocracyBot sees it and restarts, ideally not by restarting the Pi (you could run `pkill -f "$JAR_NAME"` first to kill the jar).
