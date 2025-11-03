import React from 'react';

const Footer: React.FC = () => {
  return (
    <footer className="bg-gray-50 rounded-xl p-6 text-center mt-8">
      <div className="text-gray-600 mb-2">
        <p className="text-sm">문제가 발생하거나 개선 사항이 있으시면 언제든지 연락해 주세요.</p>
      </div>
      <div className="text-indigo-600 font-medium">
         버그 제보 및 문의: <a href="mailto:hjs90561@gmail.com" className="hover:underline">hjs90561@gmail.com</a>
      </div>
      <div className="text-xs text-gray-400 mt-2">
        GGeolmuse • Built with Spring Boot & React
      </div>
    </footer>
  );
};

export default Footer;
