import { type APIBaseInteraction } from 'discord-api-types/v10';

export abstract class BaseCommand {
	abstract handle(interaction: APIBaseInteraction<any, any>): Promise<any>;
}
