# Yay
[Guide](https://discord.com/developers/docs/tutorials/hosting-on-cloudflare-workers)
[API reference](https://discord.com/developers/docs/reference)

## Development

1. `npm run ngrok`
2. Pass the ngrok URL into the bot's **Interactions Endpoint URL**
3. In another console, `npx wrangler dev --test-scheduled` or just `npm run dev`

You can then test slash commands and test scheduled events with `curl "http://localhost:8787/__scheduled?cron=*+*+*+*+*"`.

### Setup
First, add BOT_TOKEN, APP_ID, and APP_PUBLIC_KEY to your `.env`. Then run `npm run register` to register the bot's slash commands.

All slash commands will then be sent to your local machine as POST requests.

### Deploy
`npm run deploy`. Update the **Interactions Endpoint URL** with the Cloudflare prod URL.
