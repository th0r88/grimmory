import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EmailStatusService {

  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/email/status`;

  private http = inject(HttpClient);

  getFailedStatuses(bookIds: number[]): Observable<Record<number, string>> {
    return this.http.get<Record<number, string>>(this.apiUrl, {
      params: {bookIds: bookIds.join(',')}
    });
  }

  retryAutoEmail(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/retry/${bookId}`, {});
  }
}
