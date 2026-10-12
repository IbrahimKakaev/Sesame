/**
 * ApiService - Appels de l'API hors authentification et hors flux de réponse
 */

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Conversation,
  CreationCompte,
  DetailConversation,
  DocumentResume,
  Groupe,
  ModificationCompte,
  PageJournal,
  Profil,
} from '../modeles';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ==================== DOCUMENTS ====================

  documents(): Observable<DocumentResume[]> {
    return this.http.get<DocumentResume[]>('/api/documents');
  }

  deposer(fichier: File, titre: string, groupes: string[]): Observable<DocumentResume> {
    const formulaire = new FormData();
    formulaire.append('fichier', fichier);
    if (titre.trim()) formulaire.append('titre', titre.trim());
    groupes.forEach((groupe) => formulaire.append('groupes', groupe));
    return this.http.post<DocumentResume>('/api/documents', formulaire);
  }

  fichier(id: string): Observable<Blob> {
    return this.http.get(`/api/documents/${id}/fichier`, { responseType: 'blob' });
  }

  // ==================== CONVERSATIONS ====================

  conversations(): Observable<Conversation[]> {
    return this.http.get<Conversation[]>('/api/conversations');
  }

  conversation(id: string): Observable<DetailConversation> {
    return this.http.get<DetailConversation>(`/api/conversations/${id}`);
  }

  supprimerConversation(id: string): Observable<void> {
    return this.http.delete<void>(`/api/conversations/${id}`);
  }

  // ==================== ADMINISTRATION ====================

  utilisateurs(): Observable<Profil[]> {
    return this.http.get<Profil[]>('/api/admin/utilisateurs');
  }

  creerUtilisateur(demande: CreationCompte): Observable<Profil> {
    return this.http.post<Profil>('/api/admin/utilisateurs', demande);
  }

  modifierUtilisateur(id: string, demande: ModificationCompte): Observable<Profil> {
    return this.http.put<Profil>(`/api/admin/utilisateurs/${id}`, demande);
  }

  groupes(): Observable<Groupe[]> {
    return this.http.get<Groupe[]>('/api/admin/groupes');
  }

  creerGroupe(nom: string): Observable<Groupe> {
    return this.http.post<Groupe>('/api/admin/groupes', { nom });
  }

  supprimerGroupe(id: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/groupes/${id}`);
  }

  tousLesDocuments(): Observable<DocumentResume[]> {
    return this.http.get<DocumentResume[]>('/api/admin/documents');
  }

  changerGroupesDocument(id: string, groupes: string[]): Observable<DocumentResume> {
    return this.http.put<DocumentResume>(`/api/admin/documents/${id}/groupes`, { groupes });
  }

  supprimerDocument(id: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/documents/${id}`);
  }

  journal(): Observable<PageJournal> {
    return this.http.get<PageJournal>('/api/admin/journal', { params: { taille: 100 } });
  }
}
