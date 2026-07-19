export function ErrorBanner({ message }) {
  if (!message) return null;
  return (
    <div
      role="alert"
      className="border-b border-amber-200 bg-amber-50 px-4 py-1.5 text-sm text-amber-800"
    >
      Connexion à l'API impossible ({message}) — nouvelle tentative automatique…
    </div>
  );
}
