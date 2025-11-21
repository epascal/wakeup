# Calendar Reminder

Application Android qui surveille le calendrier du téléphone et affiche les rappels d'événements en plein écran, même lorsque l'écran est verrouillé.

## Fonctionnalités

- **Lecture du calendrier** : Accède aux événements et rappels du calendrier système
- **Détection automatique** : Surveille en continu les rappels à venir
- **Affichage plein écran** : Affiche les rappels même sur l'écran verrouillé
- **Gestion des rappels** : Permet de reprogrammer un rappel dans 5 minutes, 10 minutes, 30 minutes ou 1 heure
- **Bouton Done** : Permet de fermer l'écran de rappel

## Permissions requises

- `READ_CALENDAR` : Pour lire les événements et rappels du calendrier
- `SYSTEM_ALERT_WINDOW` : Pour afficher l'interface au-dessus d'autres applications
- `DISABLE_KEYGUARD` : Pour afficher sur l'écran verrouillé
- `WAKE_LOCK` : Pour réveiller l'écran lors d'un rappel
- `POST_NOTIFICATIONS` : Pour les notifications (Android 13+)

## Installation

1. Ouvrir le projet dans Android Studio
2. Synchroniser les dépendances Gradle
3. Compiler et installer sur un appareil Android (API 26+)

## Utilisation

1. Au premier lancement, l'application demandera les permissions nécessaires
2. Le service de surveillance démarre automatiquement
3. Lorsqu'un rappel d'événement est détecté, l'écran de rappel s'affiche automatiquement
4. Utiliser les boutons pour reprogrammer le rappel ou "Terminé" pour fermer

## Structure du projet

- `MainActivity` : Activité principale qui démarre le service
- `ReminderActivity` : Activité plein écran pour afficher les rappels
- `CalendarMonitorService` : Service qui surveille le calendrier en arrière-plan
- `BootReceiver` : Receiver pour redémarrer le service après un redémarrage

## Notes techniques

- Minimum SDK : 26 (Android 8.0)
- Target SDK : 34 (Android 14)
- Le service fonctionne en foreground pour garantir son exécution continue
- Utilise AlarmManager pour afficher les rappels même si l'écran est verrouillé

