import { api } from './utils';
// Required for `npm run register` because we're running this outside of wrangler
import 'dotenv/config';

const ALL_COMMANDS = [
	{
		name: 'party',
		options: [
			{
				name: 'create',
				options: [
					{
						autocomplete: false,
						name: 'name',
						description: 'Name of your party',
						type: 3,
						required: true,
						max_length: 50,
					},
					{
						autocomplete: false,
						name: 'color',
						description: 'Color name or HEX (e.g., FF5733) without number sign (#)',
						type: 3,
						required: false,
					},
				],
				description: 'Create a party',
				type: 1,
			},
			{
				name: 'join',
				options: [
					{
						autocomplete: false,
						name: 'party',
						description: 'The party to join',
						type: 8,
						required: true,
					},
				],
				description: 'Join a party',
				type: 1,
			},
			{
				name: 'info',
				options: [
					{
						autocomplete: false,
						name: 'party',
						description: 'The party to lookup',
						type: 8,
						required: true,
					},
				],
				description: 'View party info',
				type: 1,
			},
			{
				name: 'edit',
				options: [
					{
						name: 'name',
						options: [
							{
								autocomplete: false,
								name: 'name',
								description: 'Name of your party',
								type: 3,
								required: true,
								max_length: 50,
							},
						],
						description: 'Change party name',
						type: 1,
					},
					{
						name: 'color',
						options: [
							{
								autocomplete: false,
								name: 'color',
								description: 'Color name or HEX (e.g., FF5733) without number sign (#)',
								type: 3,
								required: true,
							},
						],
						description: 'Change party color',
						type: 1,
					},
					{
						name: 'ban',
						options: [
							{
								autocomplete: false,
								name: 'user',
								description: 'User to ban',
								type: 6,
								required: true,
							},
						],
						description: 'Ban a member',
						type: 1,
					},
					{
						name: 'unban',
						options: [
							{
								autocomplete: false,
								name: 'user',
								description: 'User to ban',
								type: 6,
								required: true,
							},
						],
						description: 'Unban a member',
						type: 1,
					},
					{
						name: 'invite-bot',
						options: [
							{
								autocomplete: false,
								name: 'bot',
								description: 'Bot to join',
								type: 6,
								required: true,
							},
						],
						description: 'Invites a bot to the party',
						type: 1,
					},
					{
						name: 'transfer',
						options: [
							{
								autocomplete: false,
								name: 'user',
								description: 'User to transfer the party to',
								type: 6,
								required: true,
							},
						],
						description: 'Elect a new party leader',
						type: 1,
					},
				],
				description: 'Party editing commands',
				type: 2,
			},
		],
		description: 'Party commands',
		type: 1,
	},
	{
		name: 'campaign',
		options: [{ name: 'slogan', description: 'Your campaign slogan', type: 3, required: true, max_length: 200 }],
		description: 'Run for President',
		type: 1,
	},
	{
		name: 'slogan',
		options: [{ name: 'slogan', description: 'Your new slogan', type: 3, required: true, max_length: 200 }],
		description: 'Change your slogan',
		type: 1,
	},
	{ name: 'next-election', options: [], description: 'Returns next election time', type: 1 },
	{
		name: 'propose',
		options: [
			{ name: 'amendment', description: 'The amendment to add (markdown will be escaped)', type: 3, required: true, max_length: 250 },
		],
		description: 'Propose an amendment',
		type: 1,
	},
	{
		name: 'repeal',
		options: [{ min_value: 1, name: 'amendment-number', description: 'The amendment number to repeal', type: 4, required: true }],
		description: 'Repeal / unrepeal an amendment',
		type: 1,
	},
	{
		name: 'refer',
		options: [{ min_value: 1, name: 'amendment-number', description: 'The amendment number to refer to', type: 4, required: true }],
		description: 'Sends the amendment in chat',
		type: 1,
	},
	{
		name: 'impeach',
		options: [{ name: 'reason', description: 'Why impeachment is deserved', type: 3, required: true, max_length: 250 }],
		description: 'Impeach the President',
		type: 1,
	},
	{
		name: 'naturalize',
		options: [{ name: 'user', description: 'The user to naturalize', type: 6, required: true }],
		description: 'Naturalize an immigrant',
		type: 1,
	},
];

// `npm run register` will add the commands to the bot
// This is calling the bulk overwrite endpoint: https://discord.com/developers/docs/interactions/application-commands#bulk-overwrite-global-application-commands
api(`applications/${process.env.APP_ID}/commands`, { method: 'PUT', body: ALL_COMMANDS });
