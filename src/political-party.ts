export class PoliticalParty {
	// Also serves as the party ID
	private role: string;
	private leader: string;

	private blacklist: string[] = [];

	constructor(role: string, leader: string) {
		this.role = role;
		this.leader = leader;
	}

	public isBanned(memberID: string): boolean {
		return this.blacklist.includes(memberID);
	}

	public getBlacklist(): string[] {
		return this.blacklist;
	}

	/**
	 * Adds this user to the party blacklist.
	 *
	 * Does **NOT** perform any API calls.
	 *
	 * @param member Discord user ID
	 * @returns true if user was blacklisted, false if already banned
	 */
	public addBan(member: string): boolean {
		if (this.isBanned(member)) return false;
		this.blacklist.push(member);
		return true;
	}

	public removeBan(member: string): boolean {
		const index = this.blacklist.indexOf(member);
		if (index !== -1) {
			this.blacklist.splice(index, 1);
			return true;
		}
		return false;
	}

	/**
	 * Gets the role ID of this party.
	 * @returns the Discord role ID of this party
	 */
	public getID(): string {
		return this.role;
	}

	public getLeaderID(): string {
		return this.leader;
	}

	public setLeaderID(memberID: string) {
		this.leader = memberID;
	}

	public equals(party: PoliticalParty | null): boolean {
		return party !== null && party.getID() === this.role;
	}
}
