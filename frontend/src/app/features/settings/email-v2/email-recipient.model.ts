export interface EmailRecipient {
  id: number;
  email: string;
  name: string;
  defaultRecipient: boolean;
  isEditing: boolean;
  userId?: number;
  ownerUsername?: string;
}
