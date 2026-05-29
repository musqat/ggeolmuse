import React from 'react';

const Footer: React.FC = () => {
  return (
    <footer className="border-t border-line py-8 mt-auto">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-3">
        <p className="text-[13px] text-tx-3">
          GGeolmuse · 모의투자 플랫폼
        </p>
        <p className="text-[13px] text-tx-3">
          버그 제보:{' '}
          <a href="mailto:hjs90561@gmail.com" className="text-brand hover:text-brand-light transition-colors">
            hjs90561@gmail.com
          </a>
        </p>
      </div>
    </footer>
  );
};

export default Footer;
