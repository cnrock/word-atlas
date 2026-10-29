/// <reference types="vite/client" />

interface Window {
  WordAtlasAndroid?: { saveBackup(json: string): void; shareImage(dataUrl: string, filename: string): void };
}
