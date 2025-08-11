# Yay
[Guide](https://discord.com/developers/docs/tutorials/hosting-on-cloudflare-workers)
[API reference](https://discord.com/developers/docs/reference)

## Development

### Setup
First, add BOT_TOKEN, APP_ID, and APP_PUBLIC_KEY to your `.env`. Then run `npm run register` to register the bot's slash commands.

### Development
1. `npm run ngrok`
2. Pass the ngrok URL into the bot's **Interactions Endpoint URL**
3. In a second terminal: `npm run dev`

All slash commands will then be sent to your local machine as POST requests.

### Deploy
`npm run deploy`. Update the **Interactions Endpoint URL** with the Cloudflare prod URL.
