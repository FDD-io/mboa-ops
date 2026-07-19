import { BrowserRouter, NavLink, Route, Routes } from 'react-router-dom';
import { HomePage } from './pages/HomePage';
import { PatronPage } from './pages/PatronPage';

const lienStyle = ({ isActive }) =>
  `rounded-lg px-3 py-1.5 text-sm font-medium ${
    isActive
      ? 'bg-emerald-600 text-white'
      : 'text-slate-600 hover:bg-slate-200'
  }`;

export default function App() {
  return (
    <BrowserRouter>
      <div className="flex h-dvh flex-col">
        <header className="flex items-center gap-4 border-b border-slate-200 bg-white px-4 py-2.5">
          <span className="text-base font-bold tracking-tight text-slate-900">
            MBOA<span className="text-emerald-600">-OPS</span>
          </span>
          <nav className="flex gap-1" aria-label="Navigation principale">
            <NavLink to="/" end className={lienStyle}>
              Opérations
            </NavLink>
            <NavLink to="/patron" className={lienStyle}>
              Patron
            </NavLink>
          </nav>
        </header>
        <main className="min-h-0 flex-1 overflow-y-auto">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/patron" element={<PatronPage />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
