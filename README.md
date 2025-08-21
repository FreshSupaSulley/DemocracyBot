# DemocracyBot

A Discord bot that runs a server and distributes power to an elected President every month. Made for the people of the nation of Discordias.

For years, this was ran on a ~~shitty~~ wonderful and timeless Raspberry Pi running JDA indefinitely. Now, we're on Cloudflare workers (thank god for their free tier).

![Our nation's glorious flag](https://github.com/FreshSupaSulley/DemocracyBot/assets/45902499/3278dc05-3fbc-414f-8717-b233af1c312b)

# Make your own server

Use the server template: https://discord.new/qjjWS4zBUF2a. Then make your own Cloudflare Worker and wire it all together (see [building](#building)).

## Legacy Dbot

Just for me: if you ever need to go back to the pi, ssh into it and run `sudo nano /home/pi/.bashrc`. Scroll to the bottom and uncomment the `java -jar` line.
To autoconnect to a WiFi network, add it using `sudo nano /etc/wpa_supplicant/wpa_supplicant.conf` (or I think you can just connect and it'll save it here automatically).

## Building

### Resources

[Guide](https://discord.com/developers/docs/tutorials/hosting-on-cloudflare-workers)
[API reference](https://discord.com/developers/docs/reference)

### Setup

First, add BOT_TOKEN, APP_ID, and APP_PUBLIC_KEY to your `.env` just for testing (and ENV=DEV). Make sure you do the same with `npx wrangler secret put <KEY>` for all of them too. Then run `npm run register` to register the bot's slash commands.

### Running

1. `npm run ngrok`
2. Pass the ngrok URL into the bot's **Interactions Endpoint URL**
3. In another terminal window, `npm run dev`.

You can then test slash commands and test scheduled events with `curl "http://localhost:8787/__scheduled?cron=*+*+*+*+*"`.

### Deploy

`npm run deploy`. Update your bot's **Interactions Endpoint URL** with the Cloudflare URL.
