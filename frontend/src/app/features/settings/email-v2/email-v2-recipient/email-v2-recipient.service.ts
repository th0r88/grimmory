import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG } from '../../../../core/config/api-config';
import {EmailRecipient} from '../email-recipient.model';

export interface GetRecipientsOptions {
  userId?: number;
  scopeAll?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class EmailV2RecipientService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/email/recipients`;

  private http = inject(HttpClient);

  getRecipients(opts?: GetRecipientsOptions): Observable<EmailRecipient[]> {
    let params = new HttpParams();
    if (opts?.scopeAll) {
      params = params.set('scope', 'all');
    } else if (opts?.userId !== undefined) {
      params = params.set('userId', opts.userId);
    }
    return this.http.get<EmailRecipient[]>(this.url, { params });
  }

  createRecipient(recipient: EmailRecipient): Observable<EmailRecipient> {
    return this.http.post<EmailRecipient>(this.url, recipient);
  }

  updateRecipient(recipient: EmailRecipient): Observable<EmailRecipient> {
    return this.http.put<EmailRecipient>(`${this.url}/${recipient.id}`, recipient);
  }

  deleteRecipient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  setDefaultRecipient(id: number): Observable<void> {
    return this.http.patch<void>(`${this.url}/${id}/set-default`, {});
  }
}
