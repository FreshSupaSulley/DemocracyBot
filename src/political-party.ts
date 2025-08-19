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

	public getRoleID(): string {
		return this.role;
	}

	public getLeaderID(): string {
		return this.leader;
	}

	public setLeaderID(memberID: string) {
		this.leader = memberID;
	}

	public equals(party: PoliticalParty | null): boolean {
		return party !== null && party.getRoleID() === this.role;
	}
}
