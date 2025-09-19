import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Header from '@components/common/Header';
import Dashboard from '@pages/Dashboard';
import Portfolio from '@pages/Portfolio';
import Trading from '@pages/Trading';
import Backtest from '@pages/Backtest';
import StockDetail from '@pages/StockDetail';
import Account from '@pages/Account';

function App() {
  return (
    <Router>
      <div className="App min-h-screen bg-gray-50">
        <Header />
        <main>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/stock/:symbol" element={<StockDetail />} />
            <Route path="/portfolio" element={<Portfolio />} />
            <Route path="/backtest" element={<Backtest />} />
            <Route path="/trading" element={<Trading />} />
            <Route path="/account" element={<Account />} />
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App
